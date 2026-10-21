package com.example.somnus.domain.assistant

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantModelProviderTest {

    @Test
    fun deterministicProvider_reportsAvailableAndGeneratesResponse() = runTest {
        val provider = DeterministicLocalAssistantModelProvider()
        assertTrue(provider.isAvailable)
        assertFalse(provider.isNeuralModel)
        assertEquals("Local rules assistant", provider.modelName)

        val context = PromptContext(
            systemPrompt = "You are Somnus assistant.",
            compactDataContext = "Recorded sleep: 8h 0m",
            conversationContext = "",
            fullPrompt = "How has my sleep been this week?",
            estimatedInputTokens = 45,
            dataReferences = emptyList()
        )

        val chunk = provider.generateResponse(context).first()
        assertTrue(chunk.isComplete)
        assertNotNull(chunk.text)
        assertTrue(chunk.text.contains("8h 0m"))
        assertTrue(chunk.inputTokensUsed > 0)
        assertTrue(chunk.outputTokensUsed > 0)
    }

    @Test
    fun androidAiCoreProvider_whenForcedUnavailable_reportsUnavailableAndThrowsOnGenerate() = runTest {
        val provider = AndroidAiCoreAssistantModelProvider(forceAvailableForTesting = false)
        assertFalse(provider.isAvailable)
        assertTrue(provider.isNeuralModel)
        assertEquals("Gemini Nano (AICore)", provider.modelName)

        val context = PromptContext(
            systemPrompt = "System",
            compactDataContext = "Steps: 5,000",
            conversationContext = "",
            fullPrompt = "Analyze my steps",
            estimatedInputTokens = 20,
            dataReferences = emptyList()
        )

        var thrown = false
        try {
            provider.generateResponse(context).first()
        } catch (e: IllegalStateException) {
            thrown = true
            assertTrue(e.message?.contains("AI features unavailable on this device") == true)
        }
        assertTrue(thrown)
    }

    @Test
    fun androidAiCoreProvider_whenForcedAvailable_generatesResponse() = runTest {
        val provider = AndroidAiCoreAssistantModelProvider(forceAvailableForTesting = true)
        assertTrue(provider.isAvailable)
        assertTrue(provider.isNeuralModel)

        val context = PromptContext(
            systemPrompt = "System",
            compactDataContext = "Steps: 10,000",
            conversationContext = "",
            fullPrompt = "Summarize my day",
            estimatedInputTokens = 25,
            dataReferences = emptyList()
        )

        val chunk = provider.generateResponse(context).first()
        assertTrue(chunk.isComplete)
        assertTrue(chunk.text.contains("Gemini Nano"))
        assertTrue(chunk.text.contains("Steps: 10,000"))
    }
}
