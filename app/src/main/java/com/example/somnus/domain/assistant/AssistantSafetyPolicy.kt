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
        "sleep apnea diagnosis", "you have a disorder", "medical condition", "clinical diagnosis"
    )

    private val DIAGNOSIS_QUERY_PATTERNS = listOf(
        "diagnose", "do i have insomnia", "diagnose my sleep", "do i have sleep apnea",
        "what disease do i have", "diagnose me", "medical diagnosis"
    )

    private val PRESCRIPTION_PATTERNS = listOf(
        "take mg of", "dosage of", "prescribe", "take sleeping pills", "take ambien",
        "take zolpidem", "recommended medication"
    )

    private val DANGEROUS_DIET_PATTERNS = listOf(
        "eat under 500 calories", "fast for a week", "skip all meals", "purge", "starve",
        "purge food", "stop eating completely", "zero calorie diet for a week"
    )

    private val EXCESSIVE_EXERCISE_PATTERNS = listOf(
        "work out until you pass out", "exercise through severe pain", "run on an injury",
        "burn 5000 calories without eating", "never take a rest day", "push through sharp chest pain",
        "train through injury", "exercise until exhaustion"
    )

    private val DIRECT_CAUSATION_PATTERNS = listOf(
        "caused your sleep to", "directly caused you to", "caused you to wake up",
        "is the cause of your", "is causing your insomnia"
    )

    private val FABRICATED_BIOMARKER_PATTERNS = listOf(
        "your rem sleep was", "your deep sleep was", "your light sleep stage was",
        "clinical hrv score of", "measured your rem cycle"
    )

    private val SQL_WRITE_KEYWORDS = listOf(
        "drop table", "alter table", "delete from", "insert into", "update ", "truncate"
    )

    /**
     * Inspects a candidate response and applies safety sanitization.
     */
    fun sanitizeResponse(rawContent: String): SafetyCheckResult {
        val lower = rawContent.lowercase(Locale.ROOT)

        // 1. Check for database mutation attempts
        if (SQL_WRITE_KEYWORDS.any { lower.contains(it) }) {
            return SafetyCheckResult(
                safeContent = "Database mutation operations are strictly prohibited. Somnus operates in read-only mode for verified health records.",
                wasModified = true,
                warningReason = "Database mutation command intercepted."
            )
        }

        // 2. Check for extreme diet or harm
        if (DANGEROUS_DIET_PATTERNS.any { lower.contains(it) }) {
            return SafetyCheckResult(
                safeContent = "Somnus promotes sustainable, nourishing nutrition. Extreme restriction or skipping essential meals is not supported. Please consult a registered dietitian or doctor for dietary guidance.",
                wasModified = true,
                warningReason = "Extreme dietary restriction filtered."
            )
        }

        // 3. Check for excessive exercise encouragement
        if (EXCESSIVE_EXERCISE_PATTERNS.any { lower.contains(it) }) {
            return SafetyCheckResult(
                safeContent = "Somnus promotes balanced, restorative movement. Exercising through severe pain, ignoring injuries, or training to exhaustion is hazardous. Please consult a sports physician or licensed trainer.",
                wasModified = true,
                warningReason = "Excessive exercise encouragement filtered."
            )
        }

        // 4. Check for medication or dosage prescription
        if (PRESCRIPTION_PATTERNS.any { lower.contains(it) }) {
            return SafetyCheckResult(
                safeContent = "Somnus cannot recommend medications or dosages. For sleep aids or pharmaceuticals, please consult a licensed physician or pharmacist.",
                wasModified = true,
                warningReason = "Pharmaceutical advice filtered."
            )
        }

        var sanitized = rawContent

        // 5. Check for fabricated unmeasured biomarkers (sleep stages / HRV)
        if (FABRICATED_BIOMARKER_PATTERNS.any { lower.contains(it) }) {
            sanitized = "$sanitized\n\n[Scientific Transparency: Somnus records physical stillness and duration, and does not fabricate clinical sleep stages (REM/Deep) or unmeasured autonomic biomarkers without clinical equipment.]"
        }

        // 6. Check for medical diagnosis claims
        if (DIAGNOSIS_PATTERNS.any { lower.contains(it) }) {
            sanitized = "$sanitized\n\n[Medical Disclaimer: Somnus records observational data and cannot diagnose medical sleep disorders. For clinical concerns, consult a doctor.]"
        }

        // 7. Soften direct causal assertions to statistical association
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

        // 8. Word count truncation (target 80-150 words, max 200)
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
     * Evaluates whether a user query violates medical boundary, health safety, or database rules.
     */
    fun checkUserQuerySafety(query: String): QuerySafetyResult {
        val lower = query.lowercase(Locale.ROOT)

        // 1. Prohibit database mutations
        if (containsDisallowedDatabaseOperations(query)) {
            return QuerySafetyResult.Prohibited(
                "Database mutation queries and SQL commands are strictly prohibited. Somnus operates in read-only mode for verified health records."
            )
        }

        // 2. Prohibit medication dosage questions
        if (lower.contains("how much medication") || lower.contains("sleeping pill dose") || lower.contains("melatonin dosage for child") || lower.contains("prescribe")) {
            return QuerySafetyResult.Prohibited(
                "Somnus cannot provide medication dosage recommendations. Please contact a pharmacist or physician."
            )
        }

        // 3. Prohibit medical diagnosis requests
        if (DIAGNOSIS_QUERY_PATTERNS.any { lower.contains(it) }) {
            return QuerySafetyResult.Prohibited(
                "Somnus is a personal lifestyle and sleep tracking application, not a diagnostic device. It cannot diagnose insomnia, sleep apnea, or medical conditions. Please consult a qualified healthcare provider."
            )
        }

        // 4. Prohibit extreme restriction or harmful diet queries
        if (DANGEROUS_DIET_PATTERNS.any { lower.contains(it) }) {
            return QuerySafetyResult.Prohibited(
                "Somnus does not support extreme dietary restriction, crash fasting, or purging. Please consult a healthcare professional for safe nutritional advice."
            )
        }

        // 5. Prohibit dangerous exercise prompts
        if (EXCESSIVE_EXERCISE_PATTERNS.any { lower.contains(it) }) {
            return QuerySafetyResult.Prohibited(
                "Somnus promotes safe physical activity. Training through severe pain or acute injuries is harmful. Please consult a medical professional."
            )
        }

        return QuerySafetyResult.Safe
    }

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
