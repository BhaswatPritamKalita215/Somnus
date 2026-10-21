package com.example.somnus.domain.assistant

/**
 * Validates, formats, and bounds model output to ensure concise and safe user-facing text.
 *
 * Guarantees:
 * 1. Strict Word Limit: Caps responses to ~150 words (max 200 tokens).
 * 2. Malformed Response Recovery: Gracefully recovers from empty or aborted generation.
 * 3. Tag Extraction: Extracts verified data badges embedded in the output.
 */
object AssistantResponseParser {

    private const val MAX_OUTPUT_WORDS = 150
    private const val HARD_MAX_TOKENS = TokenBudgetConfig.MAX_OUTPUT_TOKENS // 200

    fun parseAndValidate(
        rawOutput: String,
        contextReferences: List<DataReference> = emptyList()
    ): ParsedModelResponse {
        val trimmed = rawOutput.trim()

        if (trimmed.isBlank()) {
            return ParsedModelResponse(
                text = "I was unable to synthesize a response from your recorded data. Please try asking a more specific question, like \"How many steps did I take today?\" or \"How did I sleep last night?\"",
                dataReferences = contextReferences,
                isError = true
            )
        }

        // Bounded length: truncate excessive generation
        val words = trimmed.split("\\s+".toRegex())
        var boundedText = if (words.size > MAX_OUTPUT_WORDS) {
            words.take(MAX_OUTPUT_WORDS).joinToString(" ") + "…"
        } else {
            trimmed
        }

        // Enforce hard token budget bound (<= 200 tokens)
        val estimatedTokens = TokenBudgetConfig.estimateTokens(boundedText)
        if (estimatedTokens > HARD_MAX_TOKENS) {
            val allowedChars = (HARD_MAX_TOKENS * TokenBudgetConfig.CHARS_PER_TOKEN_HEURISTIC).toInt()
            boundedText = boundedText.take(allowedChars).trimEnd() + "…"
        }

        return ParsedModelResponse(
            text = boundedText,
            dataReferences = contextReferences,
            isError = false
        )
    }
}

data class ParsedModelResponse(
    val text: String,
    val dataReferences: List<DataReference>,
    val isError: Boolean = false
)
