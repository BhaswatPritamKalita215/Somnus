package com.example.somnus.domain.assistant

import java.time.LocalDate
import java.util.Locale

/**
 * Intelligent pure-Kotlin request router that determines whether an inquiry can be answered
 * deterministically by querying local repositories without invoking an AI language model.
 *
 * Guarantees:
 * 1. Zero unnecessary inference: Standard lookups (steps, sleep, calories) completely bypass the model.
 * 2. Mathematical honesty: Factual numbers are always queried from SQLite Room tables, never hallucinated.
 * 3. Health safety: Medical diagnostic inquiries are intercepted and routed to a dedicated safety refusal.
 */
object IntentRouter {

    private val MEDICAL_KEYWORDS = listOf(
        "diagnose", "diagnosis", "disease", "illness", "cure", "prescribe", "prescription",
        "medication", "medicine", "sleeping pill", "ambien", "melatonin dose", "doctor",
        "insomnia", "apnea", "sleep apnea", "disorder", "pathology", "treatment", "pain relief", "medical advice"
    )

    private val GREETING_PATTERNS = listOf(
        "hi", "hello", "hey", "help", "who are you", "what can you do", "commands",
        "good morning", "good evening", "capabilities", "guide"
    )

    private val SYNTHESIS_PATTERNS = listOf(
        "why do i", "why am i", "how come", "unrefreshed", "feel tired", "feeling tired",
        "tips", "improve my", "sleep better", "how to improve"
    )

    private val STEP_KEYWORDS = listOf(
        "step", "steps", "walk", "walked", "walking", "distance", "stride", "active minutes", "pedometer"
    )

    private val SLEEP_KEYWORDS = listOf(
        "sleep", "slept", "bedtime", "wake time", "wake up", "woke", "nap", "latency", "sol", "psqi", "tib", "time in bed"
    )

    private val NUTRITION_KEYWORDS = listOf(
        "nutrition", "calorie", "calories", "kcal", "ate", "eat", "food", "meal", "breakfast", "lunch", "dinner",
        "snack", "protein", "carb", "carbs", "carbohydrate", "fat", "macro", "macros", "diet"
    )

    private val ENERGY_KEYWORDS = listOf(
        "burned", "burn", "active calorie", "energy expenditure", "metabolic", "bmr", "active burn"
    )

    private val CROSS_METRIC_KEYWORDS = listOf(
        "affect sleep", "improve sleep", "activity vs sleep", "sleep vs activity", "steps vs sleep",
        "energy balance", "burn vs intake", "intake vs burn", "correlation", "relationship between", "more steps sleep better"
    )

    private val MISSING_DATA_KEYWORDS = listOf(
        "why is my sleep missing", "why no sleep", "why no steps", "why zero", "missing data", "data missing", "sleep data missing",
        "didn't record", "did not record", "lost data", "untracked", "data gaps", "data gap",
        "zero steps", "zero sleep", "zero recorded", "no steps recorded", "no sleep recorded"
    )

    /**
     * Routes the user's inquiry to its appropriate intent classification and date context.
     */
    fun route(
        query: String,
        currentDate: LocalDate = LocalDate.now()
    ): RoutedIntent {
        val normalized = query.trim().lowercase(Locale.ROOT)

        // 1. Safety Interception: Refuse medical diagnosis or pharmaceutical dosage advice
        if (containsAny(normalized, MEDICAL_KEYWORDS)) {
            return RoutedIntent(
                intentType = IntentType.OUT_OF_SCOPE_MEDICAL,
                isLocal = true,
                targetDate = currentDate,
                directResponse = "Somnus is a personal lifestyle and sleep tracking application, not a medical or diagnostic device. I cannot diagnose conditions, recommend medication or dosages, or provide medical advice. If you have concerns about your health, sleep disorders, or symptoms, please consult a qualified healthcare professional."
            )
        }

        // 2. Greeting and capabilities
        if (isGreetingOrHelp(normalized)) {
            return RoutedIntent(
                intentType = IntentType.GREETING_OR_HELP,
                isLocal = true,
                targetDate = currentDate,
                directResponse = "Hello! I am your Somnus local health assistant. I can help you inspect your recorded data without sending any information over the internet.\n\nYou can ask me:\n• \"How did I sleep last night?\"\n• \"What are my steps today?\"\n• \"How is my nutrition today?\"\n• \"How do active days compare to my sleep?\"\n• \"Why is my sleep data missing?\""
            )
        }

        // Extract temporal scope
        val isYesterday = normalized.contains("yesterday")
        val isThisWeek = normalized.contains("this week") || normalized.contains("past 7 days") || normalized.contains("last 7 days")
        val isPastWeek = normalized.contains("last week")
        val isPastMonth = normalized.contains("30 days") || normalized.contains("past month") || normalized.contains("last month")
        val isAverageQuery = normalized.contains("average") || normalized.contains("avg") || normalized.contains("typical") || normalized.contains("mean")

        val targetDate = when {
            isYesterday -> currentDate.minusDays(1)
            else -> currentDate
        }

        val dateRange = when {
            isPastMonth -> currentDate.minusDays(29)..currentDate
            isPastWeek -> currentDate.minusDays(13)..currentDate.minusDays(7)
            isThisWeek || isAverageQuery -> currentDate.minusDays(6)..currentDate
            else -> null
        }

        // 3. Missing data explanation
        if (containsAny(normalized, MISSING_DATA_KEYWORDS)) {
            return RoutedIntent(
                intentType = IntentType.MISSING_DATA_EXPLANATION,
                isLocal = true,
                targetDate = targetDate,
                dateRange = dateRange
            )
        }

        // 4. Cross-metric correlation
        if (containsAny(normalized, CROSS_METRIC_KEYWORDS) || (containsAny(normalized, STEP_KEYWORDS) && containsAny(normalized, SLEEP_KEYWORDS))) {
            return RoutedIntent(
                intentType = IntentType.CROSS_METRIC_CORRELATION,
                isLocal = true, // We have a pure Kotlin CrossMetricInsightEngine!
                targetDate = targetDate,
                dateRange = currentDate.minusDays(13)..currentDate
            )
        }

        // 5. Open-ended Reflective / Synthesis Questions (prioritized over raw keywords)
        if (containsAny(normalized, SYNTHESIS_PATTERNS)) {
            return RoutedIntent(
                intentType = IntentType.COMPLEX_SYNTHESIS,
                isLocal = false,
                targetDate = currentDate,
                dateRange = dateRange
            )
        }

        // 6. Steps / Physical Activity
        if (containsAny(normalized, STEP_KEYWORDS)) {
            return if (dateRange != null || isYesterday) {
                RoutedIntent(
                    intentType = IntentType.HISTORICAL_STEPS,
                    isLocal = true,
                    targetDate = targetDate,
                    dateRange = dateRange
                )
            } else {
                RoutedIntent(
                    intentType = IntentType.TODAY_STEPS,
                    isLocal = true,
                    targetDate = currentDate
                )
            }
        }

        // 7. Sleep Tracking
        if (containsAny(normalized, SLEEP_KEYWORDS)) {
            return if (dateRange != null) {
                RoutedIntent(
                    intentType = IntentType.SLEEP_HISTORY,
                    isLocal = true,
                    targetDate = targetDate,
                    dateRange = dateRange
                )
            } else {
                RoutedIntent(
                    intentType = IntentType.LATEST_SLEEP,
                    isLocal = true,
                    targetDate = targetDate
                )
            }
        }

        // 8. Energy Expenditure
        if (containsAny(normalized, ENERGY_KEYWORDS)) {
            return RoutedIntent(
                intentType = IntentType.ENERGY_EXPENDITURE,
                isLocal = true,
                targetDate = targetDate,
                dateRange = dateRange
            )
        }

        // 9. Nutrition / Food Intake
        if (containsAny(normalized, NUTRITION_KEYWORDS)) {
            return if (dateRange != null || isYesterday) {
                RoutedIntent(
                    intentType = IntentType.NUTRITION_HISTORY,
                    isLocal = true,
                    targetDate = targetDate,
                    dateRange = dateRange
                )
            } else {
                RoutedIntent(
                    intentType = IntentType.TODAY_NUTRITION,
                    isLocal = true,
                    targetDate = currentDate
                )
            }
        }

        // 10. Open-ended / complex question -> Handled with compact context & model synthesis
        return RoutedIntent(
            intentType = IntentType.COMPLEX_SYNTHESIS,
            isLocal = false,
            targetDate = currentDate,
            dateRange = dateRange
        )
    }

    private fun isGreetingOrHelp(normalized: String): Boolean {
        val clean = normalized.replace(Regex("[,!?:;]"), " ").trim()
        if (GREETING_PATTERNS.any { clean == it || clean.startsWith("$it ") || clean.endsWith(" $it") }) {
            return true
        }
        return clean.length <= 4 && clean in listOf("hi", "hey", "yo")
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { keyword ->
            if (keyword.contains(" ")) {
                text.contains(keyword)
            } else {
                // Word boundary check
                val regex = Regex("\\b${Regex.escape(keyword)}\\b")
                regex.containsMatchIn(text)
            }
        }
    }
}
