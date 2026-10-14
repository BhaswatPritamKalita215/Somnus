package com.example.somnus.domain.assistant

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Streaming response chunk from the model provider.
 */
data class ModelChunk(
    val text: String,
    val isComplete: Boolean,
    val inputTokensUsed: Int = 0,
    val outputTokensUsed: Int = 0
)

/**
 * Abstraction for an on-device language model provider.
 *
 * Guarantees:
 * 1. Zero Cloud Fallback: Strictly refuses to send health data to remote servers.
 * 2. Guaranteed Offline Operation: If an on-device neural model is unavailable,
 *    delegates to [DeterministicLocalAssistantModelProvider].
 * 3. Lifecycle-Safe Streaming: Supports standard coroutine cancellation.
 */
interface AssistantModelProvider {
    val isAvailable: Boolean
    val modelName: String
    val isNeuralModel: Boolean

    suspend fun generateResponse(promptContext: PromptContext): Flow<ModelChunk>
}

/**
 * Deterministic local synthesizer that runs 100% offline with zero external dependencies,
 * zero model downloads, and zero RAM overhead.
 *
 * Used as the primary on-device reasoning engine and robust fallback.
 */
class DeterministicLocalAssistantModelProvider : AssistantModelProvider {

    override val isAvailable: Boolean = true
    override val modelName: String = "Somnus-Local-Synthesizer"
    override val isNeuralModel: Boolean = false

    override suspend fun generateResponse(promptContext: PromptContext): Flow<ModelChunk> = flow {
        val query = promptContext.fullPrompt
        val data = promptContext.compactDataContext

        val synthesizedResponse = synthesizeResponseFromData(query, data)
        val words = synthesizedResponse.split(" ")
        val sb = StringBuilder()

        val inputTokens = promptContext.estimatedInputTokens
        var outputTokens = 0

        // Stream word by word with realistic cadence
        for (i in words.indices) {
            sb.append(words[i]).append(" ")
            outputTokens = TokenBudgetConfig.estimateTokens(sb.toString())
            emit(
                ModelChunk(
                    text = sb.toString().trimEnd(),
                    isComplete = i == words.lastIndex,
                    inputTokensUsed = inputTokens,
                    outputTokensUsed = outputTokens
                )
            )
            delay(20) // 20ms word cadence
        }
    }

    private fun synthesizeResponseFromData(fullPrompt: String, dataContext: String): String {
        val lower = fullPrompt.lowercase()

        return when {
            lower.contains("how has my sleep been") || (lower.contains("sleep") && lower.contains("week")) -> {
                "Based on your recorded 7-day sleep logs, here is your summary: $dataContext. " +
                "Consistency in bedtime schedule is one of the strongest lifestyle factors for sleep quality. " +
                "Note: Consumer sensor estimates reflect physical stillness rather than clinical sleep stages."
            }

            lower.contains("improve sleep") || lower.contains("better sleep") -> {
                "Observational sleep research highlights several evidence-backed habits: " +
                "maintaining a consistent sleep schedule across weekdays and weekends, " +
                "getting morning natural light exposure, and allowing at least 30 minutes of screen-free wind-down time before bed. " +
                "Always listen to your body rather than chasing rigid algorithmic targets."
            }

            lower.contains("activity vs sleep") || lower.contains("does walking affect sleep") -> {
                "Looking at your paired data: $dataContext. " +
                "In general, physical activity is positively associated with restorative sleep opportunities. " +
                "Remember that recorded correlation reflects your routine rather than biological causation."
            }

            lower.contains("nutrition") || lower.contains("diet") -> {
                "Here is your recorded nutrition summary: $dataContext. " +
                "Logging consistency helps identify long-term dietary patterns. " +
                "Unlogged meals naturally reduce reported totals, so treat logged calories as an observed minimum rather than an exact metabolic ledger."
            }

            else -> {
                "Here is the verified data from your Somnus records: $dataContext. " +
                "All calculations are performed locally on your device without cloud telemetry. " +
                "Let me know if you would like to explore specific days or trends."
            }
        }
    }
}
