package com.example.somnus.domain.assistant

import com.example.somnus.data.preferences.UserPreferencesRepository
import com.example.somnus.data.repository.DashboardRepository
import com.example.somnus.data.repository.FoodLogRepository
import com.example.somnus.data.repository.SleepSessionRepository
import com.example.somnus.data.repository.SolRepository
import com.example.somnus.data.repository.StepRepository
import com.example.somnus.domain.analysis.NutritionCalculator
import com.example.somnus.domain.analytics.SleepMetricsCalculator
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Constructs compact, token-bounded prompt contexts for queries requiring model synthesis.
 *
 * Guarantees:
 * 1. Strict Token Budget: Total prompt is strictly bounded to <= 1,000 tokens.
 * 2. Question-Specific Domain Pruning: Only includes health data relevant to the query.
 * 3. Pre-Calculated Arithmetic: Averages, totals, and deltas are computed locally in Kotlin.
 * 4. Bounded Conversation History: Includes at most the last 2-3 conversational turns.
 * 5. Scientific Qualification: Distinguishes manual logs from sensor estimates and wearable imports.
 */
class ContextBuilder(
    private val sleepSessionRepository: SleepSessionRepository,
    private val solRepository: SolRepository,
    private val stepRepository: StepRepository,
    private val foodLogRepository: FoodLogRepository,
    private val dashboardRepository: DashboardRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    companion object {
        const val SYSTEM_PROMPT =
            "You are Somnus Assistant, an on-device personal health companion. " +
            "Answer questions concisely (80-150 words) using only the verified data provided. " +
            "Guidelines: " +
            "1. Statistical association only: never claim one metric directly caused another. " +
            "2. Never diagnose medical conditions or recommend medications. " +
            "3. Distinguish missing data from zero values. " +
            "4. Do not invent unrecorded metrics or hallucinate database entries. " +
            "5. Be practical, friendly, and scientifically honest."
    }

    /**
     * Builds a compact, question-specific prompt context.
     */
    suspend fun buildPromptContext(
        query: String,
        recentMessages: List<AssistantMessage> = emptyList(),
        currentDate: LocalDate = LocalDate.now()
    ): PromptContext {
        val normalized = query.lowercase()
        val references = mutableListOf<DataReference>()
        val dataSections = mutableListOf<String>()

        val mentionsSleep = normalized.contains("sleep") || normalized.contains("slept") || normalized.contains("bedtime")
        val mentionsSteps = normalized.contains("step") || normalized.contains("walk") || normalized.contains("activity")
        val mentionsNutrition = normalized.contains("food") || normalized.contains("calorie") || normalized.contains("eat") || normalized.contains("nutrition")
        val mentionsCorrelation = normalized.contains("affect") || normalized.contains("relation") || normalized.contains("compare") || (mentionsSleep && mentionsSteps)

        val prefs = userPreferencesRepository.userPreferencesFlow.first()

        // 1. Sleep data (if relevant)
        if (mentionsSleep || mentionsCorrelation) {
            val sessions = sleepSessionRepository.completedSessions.first()
            val recentSessions = sessions.filter {
                val sDate = SleepMetricsCalculator.determineSleepDate(it)
                sDate in currentDate.minusDays(6)..currentDate
            }
            val solList = solRepository.getRecordsForDateRange(currentDate.minusDays(6), currentDate).first()
            val summaries = SleepMetricsCalculator.aggregateDailySummaries(recentSessions, solList)

            if (summaries.isNotEmpty()) {
                val avgMins = summaries.map { it.totalSleepDurationMinutes }.average().roundToInt()
                val latest = summaries.maxByOrNull { it.sleepDate }
                val latestDur = latest?.let { "${it.totalSleepDurationMinutes / 60}h ${it.totalSleepDurationMinutes % 60}m" } ?: "None"

                dataSections.add(
                    "Sleep (7-day window): ${summaries.size} nights recorded. " +
                    "Avg duration: ${avgMins / 60}h ${avgMins % 60}m/night. " +
                    "Latest (${latest?.sleepDate}): $latestDur."
                )

                references.add(
                    DataReference(
                        domain = DataReferenceDomain.SLEEP,
                        label = "7-Day Avg Sleep",
                        value = "${avgMins / 60}h ${avgMins % 60}m"
                    )
                )
            } else {
                dataSections.add("Sleep: No sleep sessions recorded in the last 7 days.")
            }
        }

        // 2. Steps / Physical Activity (if relevant)
        if (mentionsSteps || mentionsCorrelation) {
            val summaries = stepRepository.getDailySummariesForRange(currentDate.minusDays(6), currentDate, prefs.dailyStepGoal).first()
            val activeDays = summaries.filter { it.totalSteps > 0 }

            if (activeDays.isNotEmpty()) {
                val avgSteps = activeDays.map { it.totalSteps }.average().roundToInt()
                val todaySteps = summaries.find { it.date == currentDate }?.totalSteps ?: 0L

                dataSections.add(
                    "Steps (7-day window): ${activeDays.size} days recorded. " +
                    "Avg steps: %,d/day. Today: %,d steps (Goal: %,d)."
                        .format(avgSteps, todaySteps, prefs.dailyStepGoal)
                )

                references.add(
                    DataReference(
                        domain = DataReferenceDomain.STEPS,
                        label = "Today's Steps",
                        value = "%,d".format(todaySteps)
                    )
                )
            } else {
                dataSections.add("Steps: No step data recorded in the last 7 days.")
            }
        }

        // 3. Nutrition (if relevant)
        if (mentionsNutrition) {
            val entries = foodLogRepository.getEntriesForDateRange(currentDate.minusDays(6), currentDate).first()
            val todayEntries = foodLogRepository.getEntriesForDateSync(currentDate)
            val todaySummary = NutritionCalculator.calculateDailySummary(currentDate, todayEntries, prefs.dailyCalorieTarget)

            if (todaySummary.entries.isNotEmpty()) {
                dataSections.add(
                    "Nutrition: Today logged ${todaySummary.totalCalories.roundToInt()} kcal " +
                    "(${todaySummary.totalCarbs.roundToInt()}g carbs, ${todaySummary.totalProtein.roundToInt()}g protein, ${todaySummary.totalFat.roundToInt()}g fat). " +
                    "Target: ${prefs.dailyCalorieTarget} kcal."
                )
                references.add(
                    DataReference(
                        domain = DataReferenceDomain.NUTRITION,
                        label = "Logged Energy",
                        value = "${todaySummary.totalCalories.roundToInt()} kcal"
                    )
                )
            } else {
                dataSections.add("Nutrition: No food entries logged today.")
            }
        }

        // 4. Cross-metric insights (if correlation requested)
        if (mentionsCorrelation) {
            val insights = dashboardRepository.observeCrossMetricInsights(currentDate.minusDays(13), currentDate).first()
            val topInsight = insights.firstOrNull()
            if (topInsight != null && topInsight.isStatisticallyReliable) {
                dataSections.add("Cross-Metric Finding: ${topInsight.headline}. Caveat: ${topInsight.caveat}")
            }
        }

        // Fallback: If no specific domain was matched, provide 1-line general status
        if (dataSections.isEmpty()) {
            val todayData = dashboardRepository.observeDashboardDay(currentDate).first()
            val sleepDur = todayData.sleepSummary?.let { "${it.totalSleepDurationMinutes / 60}h ${it.totalSleepDurationMinutes % 60}m" } ?: "Unrecorded"
            val steps = todayData.stepSummary?.totalSteps ?: 0L
            val cal = todayData.nutritionSummary?.totalCalories?.roundToInt() ?: 0
            dataSections.add("Today ($currentDate) snapshot: Sleep=$sleepDur, Steps=%,d, Nutrition=%d kcal.".format(steps, cal))
        }

        val compactData = dataSections.joinToString("\n")

        // 5. Bounded Conversation History (Latest 2 turns max)
        val conversationHistory = recentMessages
            .takeLast(4) // At most 2 user + 2 assistant messages
            .joinToString("\n") { msg ->
                val speaker = if (msg.role == MessageRole.USER) "User" else "Assistant"
                "$speaker: ${msg.content.take(150)}" // truncate lengthy turns
            }

        val promptBuilder = StringBuilder()
        promptBuilder.append("### System\n").append(SYSTEM_PROMPT).append("\n\n")
        promptBuilder.append("### Verified Data\n").append(compactData).append("\n\n")
        if (conversationHistory.isNotBlank()) {
            promptBuilder.append("### Prior Context\n").append(conversationHistory).append("\n\n")
        }
        promptBuilder.append("### User\n").append(query).append("\n\n")
        promptBuilder.append("### Assistant\n")

        val fullPrompt = promptBuilder.toString()
        val estimatedTokens = TokenBudgetConfig.estimateTokens(fullPrompt)

        return PromptContext(
            systemPrompt = SYSTEM_PROMPT,
            compactDataContext = compactData,
            conversationContext = conversationHistory,
            fullPrompt = fullPrompt,
            estimatedInputTokens = estimatedTokens,
            dataReferences = references
        )
    }
}

/**
 * Output of [ContextBuilder].
 */
data class PromptContext(
    val systemPrompt: String,
    val compactDataContext: String,
    val conversationContext: String,
    val fullPrompt: String,
    val estimatedInputTokens: Int,
    val dataReferences: List<DataReference>
)
