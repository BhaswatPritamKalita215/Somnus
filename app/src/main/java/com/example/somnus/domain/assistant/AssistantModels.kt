package com.example.somnus.domain.assistant

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Role of the participant in an assistant exchange.
 */
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

/**
 * Recognized domain for verified data references.
 */
enum class DataReferenceDomain(val displayName: String) {
    SLEEP("Sleep"),
    STEPS("Activity"),
    NUTRITION("Nutrition"),
    ENERGY("Energy Expenditure"),
    SYSTEM("System")
}

/**
 * Evidence chip displayed with assistant responses to link text to verified Room data.
 */
data class DataReference(
    val domain: DataReferenceDomain,
    val label: String,
    val value: String,
    val provenance: String? = null
) {
    val summary: String get() = if (provenance != null) "$label: $value ($provenance)" else "$label: $value"
}

/**
 * Recognized intent classification for user inquiries.
 */
enum class IntentType(val displayName: String, val requiresModel: Boolean) {
    GREETING_OR_HELP("Greeting & Capabilities", false),
    TODAY_STEPS("Today's Steps", false),
    HISTORICAL_STEPS("Past Steps", false),
    LATEST_SLEEP("Latest Sleep", false),
    SLEEP_HISTORY("Past Sleep", false),
    TODAY_NUTRITION("Today's Nutrition", false),
    NUTRITION_HISTORY("Past Nutrition", false),
    ENERGY_EXPENDITURE("Calories Burned", false),
    CROSS_METRIC_CORRELATION("Cross-Metric Correlation", false),
    MISSING_DATA_EXPLANATION("Missing Data Query", false),
    COMPLEX_SYNTHESIS("Personalized AI Synthesis", true),
    OUT_OF_SCOPE_MEDICAL("Medical Inquiry (Refusal)", false)
}

/**
 * Domain representation of an assistant conversation turn.
 */
data class AssistantMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Instant = Instant.now(),
    val wasLocalHandled: Boolean = false,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val latencyMs: Long = 0L,
    val intentType: IntentType? = null,
    val dataReferences: List<DataReference> = emptyList(),
    val isError: Boolean = false
) {
    val isModelBypass: Boolean get() = wasLocalHandled
    val createdAt: Instant get() = timestamp
}

/**
 * Intent evaluation result produced by [IntentRouter].
 */
data class RoutedIntent(
    val intentType: IntentType,
    val isLocal: Boolean,
    val targetDate: LocalDate? = null,
    val dateRange: ClosedRange<LocalDate>? = null,
    val directResponse: String? = null,
    val dataReferences: List<DataReference> = emptyList()
)

/**
 * Performance and token-efficiency diagnostics tracked entirely locally.
 * Never transmitted remotely.
 */
data class AssistantDiagnostics(
    val totalRequests: Int = 0,
    val localHandledRequests: Int = 0,
    val modelInvocations: Int = 0,
    val totalInputTokens: Int = 0,
    val totalOutputTokens: Int = 0,
    val cacheHits: Int = 0,
    val cacheMisses: Int = 0,
    val totalLatencyMs: Long = 0L,
    val fallbackCount: Int = 0
) {
    val totalTokens: Int get() = totalInputTokens + totalOutputTokens

    val averageLatencyMs: Long
        get() = if (totalRequests > 0) totalLatencyMs / totalRequests else 0L

    val cacheHitRatePercentage: Float
        get() {
            val totalLookups = cacheHits + cacheMisses
            return if (totalLookups > 0) (cacheHits.toFloat() / totalLookups.toFloat()) * 100f else 0f
        }

    /**
     * Estimated token savings achieved by routing deterministic queries to local Kotlin logic
     * instead of invoking an on-device/off-device language model.
     *
     * Benchmark basis:
     * An average model request consumes ~350 input prompt tokens (system prompt + compact summary + query)
     * and produces ~90 output tokens (total ~440 tokens per query).
     */
    val tokenSavingsPercentage: Float
        get() {
            if (totalRequests == 0) return 0f
            val hypotheticalTokensIfAllModel = totalRequests * 440L
            val actualTokens = totalTokens.toLong()
            val tokensSaved = (hypotheticalTokensIfAllModel - actualTokens).coerceAtLeast(0L)
            return ((tokensSaved.toFloat() / hypotheticalTokensIfAllModel.toFloat()) * 100f).coerceIn(0f, 100f)
        }

    val localBypassPercentage: Float
        get() = if (totalRequests > 0) (localHandledRequests.toFloat() / totalRequests.toFloat()) * 100f else 0f

    val modelBypassPercentage: Float get() = localBypassPercentage
    val localDeterministicCount: Int get() = localHandledRequests
    val cacheHitRate: Float get() = cacheHitRatePercentage
    val totalTokensSaved: Long
        get() = ((totalRequests * 440L) - totalTokens.toLong()).coerceAtLeast(0L)
}

/**
 * Configurable token and word budgets for compact context engineering.
 */
object TokenBudgetConfig {
    const val MAX_INPUT_TOKENS = 1000
    const val TARGET_OUTPUT_TOKENS = 120
    const val MAX_OUTPUT_TOKENS = 200
    const val CHARS_PER_TOKEN_HEURISTIC = 4.0f

    /**
     * Quick, conservative token estimation based on whitespace and character length.
     */
    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        // English words average ~4.5 chars plus whitespace. Standard subword tokenizer is ~4 chars/token.
        return (text.length / CHARS_PER_TOKEN_HEURISTIC).toInt().coerceAtLeast(1)
    }
}
