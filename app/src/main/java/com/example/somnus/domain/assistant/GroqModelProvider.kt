package com.example.somnus.domain.assistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.somnus.BuildConfig
import com.example.somnus.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks whether the physical device or emulator currently has active internet connectivity.
 */
fun isDeviceOnline(context: Context?): Boolean {
    if (context == null) return true
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (e: Exception) {
        true
    }
}

/**
 * Metadata for a model discovered dynamically from the Groq API.
 */
data class GroqModelInfo(
    val id: String,
    val contextWindow: Int,
    val active: Boolean
)

/**
 * Assistant model provider integrating with the Groq Cloud API (OpenAI-compatible)
 * for accelerated language model inference.
 *
 * Capabilities & Guarantees:
 * 1. Dynamic Model Discovery: Queries `GET https://api.groq.com/openai/v1/models` using the configured API key.
 * 2. Intelligent Model Selection: Filters chat-capable models and prioritizes the model with the largest context window.
 * 3. Token-Limit & Rate-Limit Fallback: If Groq responds with HTTP 429 or a context/token limit error,
 *    automatically cascades down to secondary candidate models.
 * 4. Resilient Local Fallback: If the API key is not configured, invalid, or the network is unreachable,
 *    transparently delegates to [DeterministicLocalAssistantModelProvider] without surfacing breaking errors.
 */
class GroqModelProvider(
    private val apiKeyProvider: suspend () -> String? = { null },
    private val localFallback: AssistantModelProvider = DeterministicLocalAssistantModelProvider(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val isOnlineProvider: () -> Boolean = { true }
) : AssistantModelProvider {

    constructor(
        userPreferencesRepository: UserPreferencesRepository,
        localFallback: AssistantModelProvider = DeterministicLocalAssistantModelProvider(),
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        baseUrl: String = DEFAULT_BASE_URL,
        isOnlineProvider: () -> Boolean = { true }
    ) : this(
        apiKeyProvider = {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            if (prefs.groqPrivacyConsentGranted) prefs.groqApiKey else null
        },
        localFallback = localFallback,
        ioDispatcher = ioDispatcher,
        baseUrl = baseUrl,
        isOnlineProvider = isOnlineProvider
    )

    constructor(
        apiKey: String,
        localFallback: AssistantModelProvider = DeterministicLocalAssistantModelProvider(),
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        baseUrl: String = DEFAULT_BASE_URL,
        isOnlineProvider: () -> Boolean = { true }
    ) : this(
        apiKeyProvider = { apiKey },
        localFallback = localFallback,
        ioDispatcher = ioDispatcher,
        baseUrl = baseUrl,
        isOnlineProvider = isOnlineProvider
    )

    override val isAvailable: Boolean = true
    override val isNeuralModel: Boolean = true

    private val _isCloudActive = MutableStateFlow(isOnlineProvider())
    override val isCloudActive: Flow<Boolean> = _isCloudActive.asStateFlow()

    @Volatile
    private var lastSelectedModelId: String? = null

    override val modelName: String
        get() = lastSelectedModelId?.let { "Groq ($it)" } ?: "Groq (Dynamic)"

    @Volatile
    private var cachedModels: List<GroqModelInfo>? = null

    override suspend fun generateResponse(promptContext: PromptContext): Flow<ModelChunk> = flow {
        val apiKey = try {
            apiKeyProvider()?.trim().orEmpty()
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isBlank()) {
            Log.d(TAG, "Groq API key not configured or consent not granted; delegating to local deterministic assistant.")
            _isCloudActive.value = false
            localFallback.generateResponse(promptContext).collect { emit(it) }
            return@flow
        }

        if (!isOnlineProvider()) {
            Log.d(TAG, "Device is offline; delegating to local deterministic assistant.")
            _isCloudActive.value = false
            localFallback.generateResponse(promptContext).collect { emit(it) }
            return@flow
        }

        // Fetch or use cached candidate models sorted by context window
        val candidateModels = getCandidateModels(apiKey)
        if (candidateModels.isEmpty()) {
            Log.w(TAG, "No candidate models available on Groq; delegating to local deterministic assistant.")
            _isCloudActive.value = false
            localFallback.generateResponse(promptContext).collect { emit(it) }
            return@flow
        }

        var successChunk: ModelChunk? = null

        // Try models sequentially (primary largest context window first, falling back on 429 / token limits)
        for (model in candidateModels) {
            try {
                Log.d(TAG, "Attempting Groq chat completion with model: ${model.id} (context: ${model.contextWindow})")
                val response = executeChatCompletion(apiKey, model.id, promptContext)
                lastSelectedModelId = model.id
                successChunk = response
                break
            } catch (e: GroqAuthException) {
                Log.w(TAG, "Groq authentication failed (401 Unauthorized): ${e.message}. Halting cloud attempts.")
                _isCloudActive.value = false
                break
            } catch (e: GroqRateLimitException) {
                Log.w(TAG, "Groq model ${model.id} hit rate limit (HTTP 429): ${e.message}. Cascading to next candidate.")
            } catch (e: GroqTokenLimitException) {
                Log.w(TAG, "Groq model ${model.id} hit token limit error: ${e.message}. Cascading to next candidate.")
            } catch (e: Exception) {
                Log.w(TAG, "Groq request with model ${model.id} encountered error: ${e.message}. Checking next candidate.")
            }
        }

        if (successChunk != null) {
            _isCloudActive.value = true
            emit(successChunk)
        } else {
            _isCloudActive.value = false
            Log.w(TAG, "All Groq model candidates exhausted or unreachable. Falling back to local deterministic assistant.")
            localFallback.generateResponse(promptContext).collect { emit(it) }
        }
    }

    /**
     * Retrieves candidate chat models sorted descending by context window.
     */
    suspend fun getCandidateModels(apiKey: String): List<GroqModelInfo> {
        val cached = cachedModels
        if (!cached.isNullOrEmpty()) {
            return cached
        }

        val fetchedResult = fetchAvailableModels(apiKey)
        if (fetchedResult.exceptionOrNull() is GroqAuthException) {
            _isCloudActive.value = false
            return emptyList()
        }
        val models = fetchedResult.getOrNull()
        val candidateModels = if (!models.isNullOrEmpty()) {
            filterAndRankChatModels(models)
        } else {
            DEFAULT_FALLBACK_MODELS
        }

        cachedModels = candidateModels
        return candidateModels
    }

    /**
     * Discovers all available models from `GET /models`.
     */
    suspend fun fetchAvailableModels(apiKey: String): Result<List<GroqModelInfo>> = withContext(ioDispatcher) {
        val sanitizedKey = apiKey.trim()
        if (sanitizedKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Groq API key cannot be blank."))
        }

        try {
            val url = URL("$baseUrl/models")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Authorization", "Bearer $sanitizedKey")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                return@withContext Result.failure(GroqAuthException("Invalid Groq API key (401 Unauthorized)"))
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                return@withContext Result.failure(IllegalStateException("Groq API returned HTTP $responseCode: $errorBody"))
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val models = parseModelsJson(responseBody)
            Result.success(models)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Executes chat completion on `POST /chat/completions`.
     */
    private suspend fun executeChatCompletion(
        apiKey: String,
        modelId: String,
        promptContext: PromptContext
    ): ModelChunk = withContext(ioDispatcher) {
        val url = URL("$baseUrl/chat/completions")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        val requestJson = JSONObject().apply {
            put("model", modelId)
            val messages = JSONArray().apply {
                put(
                    JSONObject().apply {
                        put("role", "system")
                        put(
                            "content",
                            "You are Somnus Assistant, an evidence-based sleep, activity, and nutrition health assistant. " +
                                "Provide concise, objective insights grounded strictly in the user's logged health data. " +
                                "Never provide medical diagnoses or prescribe medications."
                        )
                    }
                )
                put(
                    JSONObject().apply {
                        put("role", "user")
                        put(
                            "content",
                            "${promptContext.fullPrompt}\n\nUser Health Records Context:\n${promptContext.compactDataContext}"
                        )
                    }
                )
            }
            put("messages", messages)
            put("temperature", 0.5)
            put("max_tokens", 1024)
        }

        connection.outputStream.use { os ->
            OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }
        }

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            throw GroqAuthException("Invalid Groq API key (401 Unauthorized)")
        }
        if (responseCode == 429) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw GroqRateLimitException("Rate limit reached for model $modelId: $errorBody")
        }

        if (responseCode != HttpURLConnection.HTTP_OK) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            if (errorBody.contains("context_length_exceeded", ignoreCase = true) ||
                errorBody.contains("token_limit", ignoreCase = true) ||
                errorBody.contains("rate_limit", ignoreCase = true)
            ) {
                throw GroqTokenLimitException("Token / context limit reached for model $modelId: $errorBody")
            }
            throw IllegalStateException("Groq API returned HTTP $responseCode: $errorBody")
        }

        val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
        parseChatCompletionResponse(responseBody, promptContext.estimatedInputTokens)
    }

    /**
     * Parses the response from `/models`.
     */
    fun parseModelsJson(jsonString: String): List<GroqModelInfo> {
        val root = JSONObject(jsonString)
        val dataArray = root.optJSONArray("data") ?: return emptyList()
        val list = mutableListOf<GroqModelInfo>()

        for (i in 0 until dataArray.length()) {
            val obj = dataArray.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val contextWindow = obj.optInt("context_window", 0)
            val active = obj.optBoolean("active", true)
            if (id.isNotBlank()) {
                list.add(GroqModelInfo(id = id, contextWindow = contextWindow, active = active))
            }
        }
        return list
    }

    /**
     * Filters for active chat completion models and sorts descending by context window.
     */
    fun filterAndRankChatModels(models: List<GroqModelInfo>): List<GroqModelInfo> {
        return models.filter { model ->
            val id = model.id.lowercase()
            model.active &&
                !id.contains("whisper") &&
                !id.contains("guard") &&
                !id.contains("embed") &&
                !id.contains("tts") &&
                !id.contains("stt") &&
                model.contextWindow > 0
        }.sortedWith(
            compareByDescending<GroqModelInfo> { it.contextWindow }
                .thenBy { it.id }
        )
    }

    /**
     * Parses the chat completion response.
     */
    fun parseChatCompletionResponse(jsonString: String, estimatedInputTokens: Int): ModelChunk {
        val root = JSONObject(jsonString)
        val choices = root.optJSONArray("choices")
        val choice = choices?.optJSONObject(0)
        val message = choice?.optJSONObject("message")
        val text = message?.optString("content") ?: ""

        val usage = root.optJSONObject("usage")
        val inTokens = usage?.optInt("prompt_tokens", estimatedInputTokens) ?: estimatedInputTokens
        val outTokens = usage?.optInt("completion_tokens", TokenBudgetConfig.estimateTokens(text))
            ?: TokenBudgetConfig.estimateTokens(text)

        return ModelChunk(
            text = text,
            isComplete = true,
            inputTokensUsed = inTokens,
            outputTokensUsed = outTokens
        )
    }

    /**
     * Tests the connectivity and authentication of a provided Groq API key.
     */
    suspend fun testConnection(apiKey: String): Result<String> = withContext(ioDispatcher) {
        val sanitizedKey = apiKey.trim()
        if (sanitizedKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("API key cannot be empty."))
        }

        val modelsResult = fetchAvailableModels(sanitizedKey)
        if (modelsResult.isFailure) {
            _isCloudActive.value = false
            val error = modelsResult.exceptionOrNull()
            return@withContext Result.failure(error ?: Exception("Connection test failed."))
        }

        _isCloudActive.value = true
        val models = modelsResult.getOrNull() ?: emptyList()
        val chatModels = filterAndRankChatModels(models)

        if (chatModels.isEmpty()) {
            Result.success("Connected to Groq! (No active chat models identified, fallback defaults ready)")
        } else {
            val primary = chatModels.first()
            Result.success(
                "Connected to Groq! Found ${chatModels.size} chat models. Primary: ${primary.id} (${primary.contextWindow} tokens)"
            )
        }
    }

    companion object {
        private const val TAG = "GroqModelProvider"
        const val DEFAULT_BASE_URL = "https://api.groq.com/openai/v1"
        private const val TIMEOUT_MS = 10000
        private val USER_AGENT = "SomnusAndroid/${BuildConfig.VERSION_NAME}"

        val DEFAULT_FALLBACK_MODELS = listOf(
            GroqModelInfo("llama-3.3-70b-versatile", 131072, true),
            GroqModelInfo("llama-3.1-8b-instant", 131072, true),
            GroqModelInfo("mixtral-8x7b-32768", 32768, true),
            GroqModelInfo("gemma2-9b-it", 8192, true)
        )
    }
}

class GroqRateLimitException(message: String) : Exception(message)
class GroqTokenLimitException(message: String) : Exception(message)
class GroqAuthException(message: String) : Exception(message)
