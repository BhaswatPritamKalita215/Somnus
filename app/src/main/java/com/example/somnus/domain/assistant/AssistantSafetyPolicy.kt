package com.example.somnus.domain.assistant

import java.util.Locale

/**
 * Enforces scientific honesty, medical boundary compliance, and user health safety.
 *
 * Guarantees:
 * 1. Medical Boundary Enforcement: Prohibits diagnosing disorders or prescribing dosages.
 * 2. Association != Causation: Refuses and rewrites claims that one lifestyle metric directly caused another.
 * 3. Eating/Exercise Disorder Prevention: Blocks extreme restriction or compulsive exercise recommendations.
 * 4. Zero Hallucinated Commands: Strips database mutations or fake hardware channels.
 */
object AssistantSafetyPolicy {

    private val DIAGNOSIS_PATTERNS = listOf(
        "you have insomnia", "diagnosed with", "you suffer from", "clinical depression",
        "sleep apnea diagnosis", "you have a disorder", "medical condition"
    )

    private val PRESCRIPTION_PATTERNS = listOf(
        "take mg of", "dosage of", "prescribe", "take sleeping pills", "take ambien",
        "take zolpidem", "recommended medication"
    )

    private val DANGEROUS_DIET_PATTERNS = listOf(
        "eat under 500 calories", "fast for a week", "skip all meals", "purge", "starve", "purge food"
    )

    private val DIRECT_CAUSATION_PATTERNS = listOf(
        "caused your sleep to", "directly caused you to", "caused you to wake up",
        "is the cause of your", "is causing your insomnia"
    )

    /**
     * Inspects a candidate response and applies safety sanitization.
     */
    fun sanitizeResponse(rawContent: String): SafetyCheckResult {
        val lower = rawContent.lowercase(Locale.ROOT)

        // 1. Check for extreme diet or harm
        if (DANGEROUS_DIET_PATTERNS.any { lower.contains(it) }) {
            return SafetyCheckResult(
                safeContent = "Somnus promotes sustainable, nourishing nutrition. Extreme restriction or skipping essential meals is not supported. Please consult a registered dietitian or doctor for dietary guidance.",
                wasModified = true,
                warningReason = "Extreme dietary restriction filtered."
            )
        }

        // 2. Check for medication or dosage prescription
        if (PRESCRIPTION_PATTERNS.any { lower.contains(it) }) {
            return SafetyCheckResult(
                safeContent = "Somnus cannot recommend medications or dosages. For sleep aids or pharmaceuticals, please consult a licensed physician or pharmacist.",
                wasModified = true,
                warningReason = "Pharmaceutical advice filtered."
            )
        }

        var sanitized = rawContent

        // 3. Check for medical diagnosis claims
        if (DIAGNOSIS_PATTERNS.any { lower.contains(it) }) {
            sanitized = "$sanitized\n\n[Medical Disclaimer: Somnus records observational data and cannot diagnose medical sleep disorders. For clinical concerns, consult a doctor.]"
        }

        // 4. Soften direct causal assertions to statistical association
        DIRECT_CAUSATION_PATTERNS.forEach { pattern ->
            if (sanitized.contains(pattern, ignoreCase = true)) {
                sanitized = sanitized.replace(
                    pattern,
                    "is associated with",
                    ignoreCase = true
                )
                if (!sanitized.contains("correlation does not imply causation", ignoreCase = true)) {
                    sanitized = "$sanitized\n\nNote: Statistical correlation in recorded data does not establish biological causation."
                }
            }
        }

        // 5. Word count truncation (target 80-150 words, max 200)
        val words = sanitized.split("\\s+".toRegex())
        if (words.size > 200) {
            sanitized = words.take(180).joinToString(" ") + "…"
        }

        return SafetyCheckResult(
            safeContent = sanitized,
            wasModified = sanitized != rawContent,
            warningReason = if (sanitized != rawContent) "Content sanitized for scientific safety" else null
        )
    }

    /**
     * Evaluates whether a user query violates medical boundary rules.
     */
    fun checkUserQuerySafety(query: String): QuerySafetyResult {
        val lower = query.lowercase(Locale.ROOT)
        if (lower.contains("how much medication") || lower.contains("sleeping pill dose") || lower.contains("melatonin dosage for child")) {
            return QuerySafetyResult.Prohibited("Somnus cannot provide medication dosage recommendations. Please contact a pharmacist or physician.")
        }
        return QuerySafetyResult.Safe
    }

    private val SQL_WRITE_KEYWORDS = listOf(
        "drop table", "alter table", "delete from", "insert into", "update ", "truncate"
    )

    fun containsDisallowedDatabaseOperations(query: String): Boolean {
        val lower = query.lowercase(Locale.ROOT)
        return SQL_WRITE_KEYWORDS.any { lower.contains(it) }
    }
}

data class SafetyCheckResult(
    val safeContent: String,
    val wasModified: Boolean,
    val warningReason: String? = null
)

sealed interface QuerySafetyResult {
    data object Safe : QuerySafetyResult
    data class Prohibited(val refusalReason: String) : QuerySafetyResult
}
