package com.example.somnus.domain.assistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GroqModelProviderTest {

    private val sampleModelsJson = """
        {
          "object": "list",
          "data": [
            {
              "id": "whisper-large-v3",
              "object": "model",
              "created": 1700000000,
              "owned_by": "openai",
              "active": true,
              "context_window": 0
            },
            {
              "id": "llama-guard-3-8b",
              "object": "model",
              "created": 1700000000,
              "owned_by": "meta",
              "active": true,
              "context_window": 8192
            },
            {
              "id": "llama-3.1-8b-instant",
              "object": "model",
              "created": 1700000000,
              "owned_by": "meta",
              "active": true,
              "context_window": 131072
            },
            {
              "id": "mixtral-8x7b-32768",
              "object": "model",
              "created": 1700000000,
              "owned_by": "mistralai",
              "active": true,
              "context_window": 32768
            },
            {
              "id": "llama-3.3-70b-versatile",
              "object": "model",
              "created": 1700000000,
              "owned_by": "meta",
              "active": true,
              "context_window": 131072
            },
            {
              "id": "deprecated-model",
              "object": "model",
              "created": 1700000000,
              "owned_by": "meta",
              "active": false,
              "context_window": 4096
            }
          ]
        }
    """.trimIndent()

    private val sampleCompletionJson = """
        {
          "id": "chatcmpl-test12345",
          "object": "chat.completion",
          "created": 1700000000,
          "model": "llama-3.3-70b-versatile",
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": "Based on your logs, you slept 7 hours and 45 minutes with low restlessness."
              },
              "finish_reason": "stop"
            }
          ],
          "usage": {
            "prompt_tokens": 150,
            "completion_tokens": 42,
            "total_tokens": 192
          }
        }
    """.trimIndent()

    @Test
    fun parseModelsJson_parsesAllModelObjectsCorrectly() {
        val provider = GroqModelProvider(apiKey = "test_key")
        val models = provider.parseModelsJson(sampleModelsJson)

        assertEquals(6, models.size)
        assertEquals("whisper-large-v3", models[0].id)
        assertEquals(0, models[0].contextWindow)
        assertTrue(models[0].active)

        assertEquals("deprecated-model", models[5].id)
        assertFalse(models[5].active)
    }

    @Test
    fun filterAndRankChatModels_filtersNonChatAndRanksLargestContextFirst() {
        val provider = GroqModelProvider(apiKey = "test_key")
        val models = provider.parseModelsJson(sampleModelsJson)
        val ranked = provider.filterAndRankChatModels(models)

        // whisper (audio) and llama-guard (guard) and deprecated (inactive) must be excluded
        assertEquals(3, ranked.size)

        // Both llama-3.1-8b-instant and llama-3.3-70b-versatile have 131072, ranked ahead of mixtral-8x7b-32768
        assertEquals(131072, ranked[0].contextWindow)
        assertEquals(131072, ranked[1].contextWindow)
        assertEquals(32768, ranked[2].contextWindow)
        assertEquals("mixtral-8x7b-32768", ranked[2].id)
    }

    @Test
    fun parseChatCompletionResponse_extractsTextAndTokenUsage() {
        val provider = GroqModelProvider(apiKey = "test_key")
        val chunk = provider.parseChatCompletionResponse(sampleCompletionJson, estimatedInputTokens = 150)

        assertTrue(chunk.isComplete)
        assertEquals("Based on your logs, you slept 7 hours and 45 minutes with low restlessness.", chunk.text)
        assertEquals(150, chunk.inputTokensUsed)
        assertEquals(42, chunk.outputTokensUsed)
    }

    @Test
    fun generateResponse_whenApiKeyIsBlank_fallsBackToLocalDeterministic() = runTest {
        val provider = GroqModelProvider(apiKey = "")
        val context = PromptContext(
            systemPrompt = "System",
            compactDataContext = "Recorded sleep: 8h 15m",
            conversationContext = "",
            fullPrompt = "How was my sleep?",
            estimatedInputTokens = 25,
            dataReferences = emptyList()
        )

        val chunk = provider.generateResponse(context).first()
        assertTrue(chunk.isComplete)
        assertNotNull(chunk.text)
        // Local deterministic assistant synthesizes the answer from compactDataContext
        assertTrue(chunk.text.contains("8h 15m"))
    }

    @Test
    fun generateResponse_whenNetworkUnreachable_fallsBackToLocalDeterministicWithoutCrashing() = runTest {
        // Points to an invalid unreachable local port
        val provider = GroqModelProvider(
            apiKey = "gsk_invalid_test_key",
            baseUrl = "http://127.0.0.1:54321/v1"
        )
        val context = PromptContext(
            systemPrompt = "System",
            compactDataContext = "Steps: 8,420",
            conversationContext = "",
            fullPrompt = "How is my activity vs sleep?",
            estimatedInputTokens = 30,
            dataReferences = emptyList()
        )

        val chunk = provider.generateResponse(context).first()
        assertTrue(chunk.isComplete)
        assertNotNull(chunk.text)
        assertTrue(chunk.text.contains("Steps: 8,420"))
    }

    @Test
    fun testConnection_withBlankKey_returnsFailure() = runTest {
        val provider = GroqModelProvider(apiKey = "")
        val result = provider.testConnection("   ")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("API key cannot be empty") == true)
    }

    @Test
    fun generateResponse_whenPrivacyConsentNotGranted_delegatesToLocalDeterministic() = runTest {
        // When consent is not granted, apiKeyProvider returns null
        val provider = GroqModelProvider(apiKeyProvider = { null })
        val context = PromptContext(
            systemPrompt = "System",
            compactDataContext = "Recorded sleep: 7h 30m",
            conversationContext = "",
            fullPrompt = "How was my sleep?",
            estimatedInputTokens = 25,
            dataReferences = emptyList()
        )

        val chunk = provider.generateResponse(context).first()
        assertTrue(chunk.isComplete)
        assertTrue(chunk.text.contains("7h 30m"))
        assertFalse(provider.isCloudActive.first())
    }

    @Test
    fun generateResponse_whenOffline_delegatesToLocalDeterministic() = runTest {
        val provider = GroqModelProvider(
            apiKey = "gsk_valid_looking_key",
            isOnlineProvider = { false }
        )
        val context = PromptContext(
            systemPrompt = "System",
            compactDataContext = "Steps: 10,000",
            conversationContext = "",
            fullPrompt = "Did I hit step goal?",
            estimatedInputTokens = 20,
            dataReferences = emptyList()
        )

        assertFalse(provider.isCloudActive.first())
        val chunk = provider.generateResponse(context).first()
        assertTrue(chunk.isComplete)
        assertTrue(chunk.text.contains("10,000"))
        assertFalse(provider.isCloudActive.first())
    }
}
