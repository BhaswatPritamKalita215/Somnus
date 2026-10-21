package com.example.somnus.domain.assistant

import com.example.somnus.data.healthconnect.HealthConnectManager
import com.example.somnus.data.preferences.UserPreferencesRepository
import com.example.somnus.data.repository.DashboardRepository
import com.example.somnus.data.repository.FoodLogRepository
import com.example.somnus.data.repository.SleepSessionRepository
import com.example.somnus.data.repository.SolRepository
import com.example.somnus.data.repository.StepRepository
import com.example.somnus.domain.analysis.DurationCalculator
import com.example.somnus.domain.analysis.NutritionCalculator
import com.example.somnus.domain.analytics.CircularTimeCalculator
import com.example.somnus.domain.analytics.SleepMetricsCalculator
import com.example.somnus.domain.model.SleepSession
import com.example.somnus.domain.model.SolRecord
import com.example.somnus.domain.model.StepSource
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Service providing factual, zero-hallucination data queries for deterministically routed inquiries.
 *
 * Guarantees:
 * 1. Zero fabrication: Uses actual Room and Health Connect records.
 * 2. Honest qualification: Missing data is clearly reported, never converted to false zeros.
 * 3. Provenance clarity: Explicitly attributes sources (Phone Sensors vs Health Connect vs Manual).
 */
class LocalDataQueryService(
    private val sleepSessionRepository: SleepSessionRepository,
    private val solRepository: SolRepository,
    private val stepRepository: StepRepository,
    private val foodLogRepository: FoodLogRepository,
    private val dashboardRepository: DashboardRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val healthConnectManager: HealthConnectManager? = null
) {

    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    /**
     * Executes local resolution for deterministic intents.
     */
    suspend fun resolveLocalQuery(intent: RoutedIntent): LocalQueryResult {
        val targetDate = intent.targetDate ?: LocalDate.now()
        val range = intent.dateRange

        return when (intent.intentType) {
            IntentType.TODAY_STEPS, IntentType.HISTORICAL_STEPS -> {
                if (range != null) {
                    resolveStepRange(range.start, range.endInclusive)
                } else {
                    resolveStepDay(targetDate)
                }
            }

            IntentType.LATEST_SLEEP, IntentType.SLEEP_HISTORY -> {
                if (range != null) {
                    resolveSleepRange(range.start, range.endInclusive)
                } else {
                    resolveSleepDay(targetDate)
                }
            }

            IntentType.TODAY_NUTRITION, IntentType.NUTRITION_HISTORY -> {
                if (range != null) {
                    resolveNutritionRange(range.start, range.endInclusive)
                } else {
                    resolveNutritionDay(targetDate)
                }
            }

            IntentType.ENERGY_EXPENDITURE -> {
                resolveEnergyExpenditure(targetDate)
            }

            IntentType.CROSS_METRIC_CORRELATION -> {
                val start = range?.start ?: targetDate.minusDays(13)
                val end = range?.endInclusive ?: targetDate
                resolveCrossMetric(start, end)
            }

            IntentType.MISSING_DATA_EXPLANATION -> {
                resolveMissingData(targetDate)
            }

            IntentType.GREETING_OR_HELP, IntentType.OUT_OF_SCOPE_MEDICAL -> {
                LocalQueryResult(
                    response = intent.directResponse ?: "I can help you review your recorded health data.",
                    dataReferences = emptyList()
                )
            }

            IntentType.COMPLEX_SYNTHESIS -> {
                LocalQueryResult(
                    response = "This inquiry requires personalized synthesis.",
                    dataReferences = emptyList()
                )
            }
        }
    }

    private suspend fun resolveStepDay(date: LocalDate): LocalQueryResult {
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val summary = stepRepository.getDailySummary(date, prefs.dailyStepGoal).first()
        val dateLabel = if (date == LocalDate.now()) "Today" else date.format(dateFormatter)

        val references = mutableListOf<DataReference>()
        val response: String

        if (summary.totalSteps > 0) {
            val progress = summary.goalProgressPercentage.roundToInt()
            val primarySourceLabel = when {
                summary.sourcesPresent.contains(StepSource.HEALTH_CONNECT_WEARABLE) && summary.sourcesPresent.contains(StepSource.PHONE_HARDWARE) ->
                    "Reconciled (Phone + Wearable)"
                summary.sourcesPresent.contains(StepSource.HEALTH_CONNECT_WEARABLE) -> "Health Connect (Wearable)"
                else -> "Phone Hardware Sensor"
            }

            references.add(
                DataReference(
                    domain = DataReferenceDomain.STEPS,
                    label = "Total Steps",
                    value = "%,d".format(summary.totalSteps),
                    provenance = primarySourceLabel
                )
            )

            response = "$dateLabel you have recorded %,d steps towards your goal of %,d steps (%d%% completed). Source: %s."
                .format(summary.totalSteps, summary.goalSteps, progress, primarySourceLabel)
        } else {
            response = "$dateLabel no steps have been recorded yet. Step tracking counts movement while carrying your phone or imports synced records from Health Connect."
        }

        return LocalQueryResult(response, references)
    }

    private suspend fun resolveStepRange(start: LocalDate, end: LocalDate): LocalQueryResult {
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val summaries = stepRepository.getDailySummariesForRange(start, end, prefs.dailyStepGoal).first()
        val activeDays = summaries.filter { it.totalSteps > 0 }
        val daysCount = ChronoUnit.DAYS.between(start, end) + 1

        val references = mutableListOf<DataReference>()
        val response: String

        if (activeDays.isNotEmpty()) {
            val totalSteps = activeDays.sumOf { it.totalSteps }
            val avgSteps = totalSteps / activeDays.size
            val daysMeetingGoal = activeDays.count { it.isGoalReached }

            references.add(
                DataReference(
                    domain = DataReferenceDomain.STEPS,
                    label = "Average Steps",
                    value = "%,d/day".format(avgSteps),
                    provenance = "${activeDays.size}/$daysCount days with data"
                )
            )

            response = "Across the period (${start.format(dateFormatter)} – ${end.format(dateFormatter)}), you averaged %,d steps/day across %,d recorded days (goal reached on %d days)."
                .format(avgSteps, activeDays.size, daysMeetingGoal)
        } else {
            response = "No step records were found between ${start.format(dateFormatter)} and ${end.format(dateFormatter)}."
        }

        return LocalQueryResult(response, references)
    }

    private suspend fun resolveSleepDay(date: LocalDate): LocalQueryResult {
        val allSessions = sleepSessionRepository.completedSessions.first()
        val dateSessions = allSessions.filter { SleepMetricsCalculator.determineSleepDate(it) == date }
        val solRecords = solRepository.getRecordsForDateRange(date, date).first()
        val sol = solRecords.firstOrNull()

        val references = mutableListOf<DataReference>()
        val response: String
        val dateLabel = if (date == LocalDate.now().minusDays(1)) "Last night" else "On the night of ${date.format(dateFormatter)}"

        val effectiveSessions: List<SleepSession>
        val effectiveDate: LocalDate
        val effectiveSol: SolRecord?
        val effectiveSolRecords: List<SolRecord>

        if (dateSessions.isNotEmpty()) {
            effectiveSessions = dateSessions
            effectiveDate = date
            effectiveSol = sol
            effectiveSolRecords = solRecords
        } else {
            val latestSession = allSessions.maxByOrNull { it.startTime }
            if (latestSession != null) {
                effectiveDate = SleepMetricsCalculator.determineSleepDate(latestSession)
                effectiveSessions = allSessions.filter { SleepMetricsCalculator.determineSleepDate(it) == effectiveDate }
                effectiveSolRecords = solRepository.getRecordsForDateRange(effectiveDate, effectiveDate).first()
                effectiveSol = effectiveSolRecords.firstOrNull()
            } else {
                effectiveSessions = emptyList<SleepSession>()
                effectiveDate = date
                effectiveSol = null
                effectiveSolRecords = emptyList<SolRecord>()
            }
        }

        if (effectiveSessions.isNotEmpty()) {
            val summaries = SleepMetricsCalculator.aggregateDailySummaries(effectiveSessions, effectiveSolRecords)
            val daySummary = summaries.find { it.sleepDate == effectiveDate }

            val durationMinutes = daySummary?.totalSleepDurationMinutes ?: 0L
            val hours = durationMinutes / 60
            val mins = durationMinutes % 60
            val durationText = "${hours}h ${mins}m"

            val primarySession = effectiveSessions.maxByOrNull {
                Duration.between(it.startTime, it.endTime ?: it.startTime).toMillis()
            }
            val sourceLabel = primarySession?.source?.displayName ?: "Tracked Session"

            references.add(
                DataReference(
                    domain = DataReferenceDomain.SLEEP,
                    label = "Sleep Duration",
                    value = durationText,
                    provenance = sourceLabel
                )
            )

            var extraDetails = ""
            if (daySummary?.bedtime != null && daySummary.wakeTime != null) {
                extraDetails = " (Bedtime: ${daySummary.bedtime}, Wake: ${daySummary.wakeTime})"
            }

            var solDetails = ""
            if (effectiveSol != null) {
                solDetails = " Sleep onset latency was reported as ${effectiveSol.latencyMinutes} minutes."
                references.add(
                    DataReference(
                        domain = DataReferenceDomain.SLEEP,
                        label = "Sleep Onset Latency",
                        value = "${effectiveSol.latencyMinutes} min",
                        provenance = effectiveSol.method.name
                    )
                )
            }

            val prefix = if (dateSessions.isEmpty()) {
                "Your latest recorded sleep was on the night of ${effectiveDate.format(dateFormatter)}"
            } else {
                dateLabel
            }

            response = "$prefix, you recorded $durationText of sleep$extraDetails. Measurement qualification: $sourceLabel.$solDetails"
        } else {
            response = "$dateLabel, no sleep session was recorded. You can start a tracking session from the Home screen or manually add a record in History."
        }

        return LocalQueryResult(response, references)
    }

    private suspend fun resolveSleepRange(start: LocalDate, end: LocalDate): LocalQueryResult {
        val allSessions = sleepSessionRepository.completedSessions.first()
        val relevant = allSessions.filter {
            val sDate = SleepMetricsCalculator.determineSleepDate(it)
            sDate in start..end
        }
        val solRecords = solRepository.getRecordsForDateRange(start, end).first()
        val summaries = SleepMetricsCalculator.aggregateDailySummaries(relevant, solRecords)
        val validNights = summaries.filter { it.totalSleepDurationMinutes >= 60 }
        val daysCount = ChronoUnit.DAYS.between(start, end) + 1

        val references = mutableListOf<DataReference>()
        val response: String

        if (validNights.isNotEmpty()) {
            val avgMins = validNights.map { it.totalSleepDurationMinutes }.average().roundToInt()
            val avgDurationText = "${avgMins / 60}h ${avgMins % 60}m"

            references.add(
                DataReference(
                    domain = DataReferenceDomain.SLEEP,
                    label = "Average Sleep",
                    value = "$avgDurationText/night",
                    provenance = "${validNights.size}/$daysCount nights recorded"
                )
            )

            val bedtimes = validNights.mapNotNull { it.bedtime }
            val avgBedtime = CircularTimeCalculator.calculateCircularMean(bedtimes)
            val bedtimeClause = if (avgBedtime != null) {
                references.add(
                    DataReference(
                        domain = DataReferenceDomain.SLEEP,
                        label = "Average Bedtime",
                        value = avgBedtime.toString(),
                        provenance = "Circular mean across ${bedtimes.size} recorded nights"
                    )
                )
                ", with an average bedtime of $avgBedtime"
            } else ""

            response = "Between ${start.format(dateFormatter)} and ${end.format(dateFormatter)}, you recorded ${validNights.size} nights of sleep, averaging $avgDurationText per night$bedtimeClause."
        } else {
            response = "No sleep sessions of 1 hour or more were recorded between ${start.format(dateFormatter)} and ${end.format(dateFormatter)}."
        }

        return LocalQueryResult(response, references)
    }

    private suspend fun resolveNutritionDay(date: LocalDate): LocalQueryResult {
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val entries = foodLogRepository.getEntriesForDateSync(date)
        val summary = NutritionCalculator.calculateDailySummary(date, entries, prefs.dailyCalorieTarget)
        val dateLabel = if (date == LocalDate.now()) "Today" else date.format(dateFormatter)

        val references = mutableListOf<DataReference>()
        val response: String

        if (summary.entries.isNotEmpty()) {
            val totalCal = summary.totalCalories.roundToInt()
            references.add(
                DataReference(
                    domain = DataReferenceDomain.NUTRITION,
                    label = "Calorie Intake",
                    value = "$totalCal kcal",
                    provenance = "${summary.entries.size} logged items"
                )
            )

            response = "$dateLabel you logged $totalCal kcal across ${summary.entries.size} items (Target: ${summary.calorieTarget ?: 2000} kcal). Macronutrient breakdown: ${summary.totalCarbs.roundToInt()}g carbs, ${summary.totalProtein.roundToInt()}g protein, ${summary.totalFat.roundToInt()}g fat."
        } else {
            response = "$dateLabel no food entries have been logged. You can scan packaged food barcodes or log meals in the Food tab."
        }

        return LocalQueryResult(response, references)
    }

    private suspend fun resolveNutritionRange(start: LocalDate, end: LocalDate): LocalQueryResult {
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val entries = foodLogRepository.getEntriesForDateRange(start, end).first()
        val grouped = entries.groupBy { it.localDate }
        val daysWithFood = grouped.keys.size

        val references = mutableListOf<DataReference>()
        val response: String

        if (daysWithFood > 0) {
            val dailyCalories = grouped.values.map { dayEntries ->
                dayEntries.sumOf { it.calculatedCalories }
            }
            val avgCal = dailyCalories.average().roundToInt()

            references.add(
                DataReference(
                    domain = DataReferenceDomain.NUTRITION,
                    label = "Average Intake",
                    value = "$avgCal kcal/day",
                    provenance = "$daysWithFood days logged"
                )
            )

            response = "Across the period (${start.format(dateFormatter)} – ${end.format(dateFormatter)}), you logged meals on $daysWithFood days, averaging $avgCal kcal per logged day."
        } else {
            response = "No food logs were found between ${start.format(dateFormatter)} and ${end.format(dateFormatter)}."
        }

        return LocalQueryResult(response, references)
    }

    private suspend fun resolveEnergyExpenditure(date: LocalDate): LocalQueryResult {
        val dayData = dashboardRepository.observeDashboardDay(date).first()
        val energy = dayData.energyExpenditure
        val dateLabel = if (date == LocalDate.now()) "Today" else date.format(dateFormatter)

        val references = mutableListOf<DataReference>()
        val response: String

        if (energy != null && (energy.activeCaloriesKcal != null || energy.totalCaloriesKcal != null)) {
            val active = energy.activeCaloriesKcal?.roundToInt()
            val total = energy.totalCaloriesKcal?.roundToInt()

            if (active != null) {
                references.add(
                    DataReference(
                        domain = DataReferenceDomain.ENERGY,
                        label = "Active Calories Burned",
                        value = "$active kcal",
                        provenance = "Health Connect"
                    )
                )
            }
            if (total != null) {
                references.add(
                    DataReference(
                        domain = DataReferenceDomain.ENERGY,
                        label = "Total Recorded Burn",
                        value = "$total kcal",
                        provenance = "Health Connect (Active + BMR)"
                    )
                )
            }

            val activeText = if (active != null) "$active kcal of active movement" else "no active exercise"
            val totalText = if (total != null) " (total recorded burn: $total kcal)" else ""

            response = "$dateLabel Health Connect reports $activeText$totalText. Note: Active expenditure records physical workouts and activity windows, excluding unmeasured resting metabolic periods."
        } else {
            response = "$dateLabel no energy expenditure records were found in Health Connect. Ensure Health Connect permissions are granted and compatible fitness apps or wearables are connected."
        }

        return LocalQueryResult(response, references)
    }

    private suspend fun resolveCrossMetric(start: LocalDate, end: LocalDate): LocalQueryResult {
        val insights = dashboardRepository.observeCrossMetricInsights(start, end).first()
        val activityVsSleep = insights.find { it.type == com.example.somnus.domain.dashboard.CrossMetricType.ACTIVITY_VS_SLEEP_DURATION }

        val references = mutableListOf<DataReference>()
        val response: String

        if (activityVsSleep != null) {
            references.add(
                DataReference(
                    domain = DataReferenceDomain.SYSTEM,
                    label = activityVsSleep.title,
                    value = activityVsSleep.confidence.displayName,
                    provenance = "${activityVsSleep.validDaysCount} paired days"
                )
            )

            response = "${activityVsSleep.headline}\n\n${activityVsSleep.explanation}\n\nCaveat: ${activityVsSleep.caveat}"
        } else {
            response = "Somnus requires at least 3 paired days with both recorded sleep and step data to compare sleep duration across activity levels. Continue recording to generate cross-metric observations."
        }

        return LocalQueryResult(response, references)
    }

    private suspend fun resolveMissingData(date: LocalDate): LocalQueryResult {
        val dayData = dashboardRepository.observeDashboardDay(date).first()
        val dateLabel = if (date == LocalDate.now()) "Today" else date.format(dateFormatter)

        val missingItems = mutableListOf<String>()
        if (dayData.sleepSummary == null) missingItems.add("Sleep Session")
        if (dayData.stepSummary == null || dayData.stepSummary.totalSteps == 0L) missingItems.add("Step Records")
        if (dayData.nutritionSummary == null || dayData.nutritionSummary.entries.isEmpty()) missingItems.add("Food Logs")
        if (dayData.energyExpenditure?.activeCaloriesKcal == null) missingItems.add("Energy Expenditure")

        val response = if (missingItems.isEmpty()) {
            "$dateLabel has complete data recorded across Sleep, Activity, Nutrition, and Energy Expenditure."
        } else {
            "$dateLabel is missing records for: ${missingItems.joinToString(", ")}.\n\n" +
                    "• Sleep: requires an active tracking session, manual entry, or Health Connect wearable import.\n" +
                    "• Steps: requires phone hardware step tracking or Health Connect sync.\n" +
                    "• Nutrition: requires logging meals in the Food diary.\n" +
                    "• Energy: requires Health Connect permissions and compatible fitness records."
        }

        return LocalQueryResult(response, emptyList())
    }
}

/**
 * Result of a local deterministic query.
 */
data class LocalQueryResult(
    val response: String,
    val dataReferences: List<DataReference>
)
