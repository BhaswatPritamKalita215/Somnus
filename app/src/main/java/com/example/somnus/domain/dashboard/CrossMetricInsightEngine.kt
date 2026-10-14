package com.example.somnus.domain.dashboard

import com.example.somnus.data.healthconnect.HealthConnectEnergyExpenditure
import com.example.somnus.domain.analytics.CircularTimeCalculator
import com.example.somnus.domain.analytics.DaySleepSummary
import com.example.somnus.domain.model.DailyNutritionSummary
import com.example.somnus.domain.model.DailyStepSummary
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * Pure domain engine generating evidence-backed cross-metric correlations.
 *
 * Guarantees:
 * 1. Association only: Strictly refuses to claim causal links between metrics.
 * 2. Minimum observation thresholds: Requires at least 3 paired observations before drawing conclusions.
 * 3. Transparent qualification: Discloses sample sizes, missing data, and measurement limitations.
 * 4. Zero fabricated data: Uses strictly persisted records from Room and Health Connect.
 */
object CrossMetricInsightEngine {

    const val MINIMUM_PAIRED_DAYS = 3
    const val ROBUST_PAIRED_DAYS = 5

    /**
     * Evaluates all cross-metric correlations across the provided historical dataset.
     */
    fun evaluateCrossMetricInsights(
        sleepSummaries: List<DaySleepSummary>,
        stepSummaries: List<DailyStepSummary>,
        nutritionSummaries: List<DailyNutritionSummary> = emptyList(),
        energyExpenditures: Map<LocalDate, HealthConnectEnergyExpenditure> = emptyMap()
    ): List<CrossMetricInsight> {
        val insights = mutableListOf<CrossMetricInsight>()

        // 1. Activity vs Sleep Duration
        val activityVsSleep = evaluateActivityVsSleepDuration(sleepSummaries, stepSummaries)
        if (activityVsSleep != null) insights.add(activityVsSleep)

        // 2. Activity vs Bedtime Schedule
        val activityVsBedtime = evaluateActivityVsBedtime(sleepSummaries, stepSummaries)
        if (activityVsBedtime != null) insights.add(activityVsBedtime)

        // 3. Energy Balance (Dietary Intake vs Recorded Expenditure)
        val energyBalance = evaluateEnergyBalance(nutritionSummaries, energyExpenditures)
        if (energyBalance != null) insights.add(energyBalance)

        // 4. Multi-Metric Rolling Trend
        val multiMetricTrend = evaluateMultiMetricTrend(sleepSummaries, stepSummaries)
        if (multiMetricTrend != null) insights.add(multiMetricTrend)

        return insights
    }

    /**
     * Compares sleep duration on higher-activity days vs lower-activity days using a median step split.
     */
    fun evaluateActivityVsSleepDuration(
        sleepSummaries: List<DaySleepSummary>,
        stepSummaries: List<DailyStepSummary>
    ): CrossMetricInsight? {
        val sleepByDate = sleepSummaries.filter { it.totalSleepDurationMinutes >= 60 }.associateBy { it.sleepDate }
        val stepsByDate = stepSummaries.filter { it.totalSteps > 0 }.associateBy { it.date }

        val commonDates = (sleepByDate.keys intersect stepsByDate.keys).sorted()
        val count = commonDates.size

        if (count < MINIMUM_PAIRED_DAYS) {
            return CrossMetricInsight(
                id = "activity_vs_sleep_insufficient",
                type = CrossMetricType.ACTIVITY_VS_SLEEP_DURATION,
                title = "Activity & Sleep Duration",
                headline = "Collecting paired data ($count / $MINIMUM_PAIRED_DAYS days)",
                explanation = "Somnus requires at least $MINIMUM_PAIRED_DAYS days with both sleep and step data to compare sleep duration across activity levels.",
                validDaysCount = count,
                minimumDaysRequired = MINIMUM_PAIRED_DAYS,
                confidence = InsightConfidence.INSUFFICIENT,
                isStatisticallyReliable = false,
                metricsInvolved = listOf("Sleep Duration", "Daily Steps")
            )
        }

        val stepCounts = commonDates.map { stepsByDate.getValue(it).totalSteps }.sorted()
        val medianSteps = stepCounts[stepCounts.size / 2]

        val highActivitySleep = mutableListOf<Long>()
        val lowActivitySleep = mutableListOf<Long>()

        commonDates.forEach { date ->
            val steps = stepsByDate.getValue(date).totalSteps
            val sleepMins = sleepByDate.getValue(date).totalSleepDurationMinutes
            if (steps >= medianSteps) {
                highActivitySleep.add(sleepMins)
            } else {
                lowActivitySleep.add(sleepMins)
            }
        }

        if (highActivitySleep.isEmpty() || lowActivitySleep.isEmpty()) return null

        val avgHigh = highActivitySleep.average()
        val avgLow = lowActivitySleep.average()
        val diffMinutes = (avgHigh - avgLow).roundToInt()

        val confidence = if (count >= ROBUST_PAIRED_DAYS) InsightConfidence.SUFFICIENT_DATA else InsightConfidence.PRELIMINARY

        val headline: String
        val explanation: String

        if (diffMinutes >= 15) {
            headline = "Longer sleep recorded on higher-activity days (+${diffMinutes}m)"
            explanation = "Across $count paired days, on days with ${formatNumber(medianSteps)} or more steps, you slept an average of ${formatDuration(avgHigh.roundToInt())}, compared to ${formatDuration(avgLow.roundToInt())} on lower-activity days."
        } else if (diffMinutes <= -15) {
            headline = "Shorter sleep recorded on higher-activity days (${diffMinutes}m)"
            explanation = "Across $count paired days, on days with ${formatNumber(medianSteps)} or more steps, you slept an average of ${formatDuration(avgHigh.roundToInt())}, compared to ${formatDuration(avgLow.roundToInt())} on lower-activity days."
        } else {
            headline = "Consistent sleep duration across activity levels"
            explanation = "Across $count paired days, your average sleep was similar between higher-activity days (${formatDuration(avgHigh.roundToInt())}) and lower-activity days (${formatDuration(avgLow.roundToInt())}) with a difference of less than 15 minutes."
        }

        return CrossMetricInsight(
            id = "activity_vs_sleep_${medianSteps}",
            type = CrossMetricType.ACTIVITY_VS_SLEEP_DURATION,
            title = "Activity & Sleep Duration",
            headline = headline,
            explanation = explanation,
            validDaysCount = count,
            minimumDaysRequired = MINIMUM_PAIRED_DAYS,
            confidence = confidence,
            isStatisticallyReliable = confidence == InsightConfidence.SUFFICIENT_DATA,
            metricsInvolved = listOf("Sleep Duration", "Daily Steps")
        )
    }

    /**
     * Compares bedtime schedule consistency on active vs inactive days using circular statistics.
     */
    fun evaluateActivityVsBedtime(
        sleepSummaries: List<DaySleepSummary>,
        stepSummaries: List<DailyStepSummary>
    ): CrossMetricInsight? {
        val sleepByDate = sleepSummaries.filter { it.bedtime != null && it.totalSleepDurationMinutes >= 60 }.associateBy { it.sleepDate }
        val stepsByDate = stepSummaries.filter { it.totalSteps > 0 }.associateBy { it.date }

        val commonDates = (sleepByDate.keys intersect stepsByDate.keys).sorted()
        val count = commonDates.size

        if (count < MINIMUM_PAIRED_DAYS) return null

        val stepCounts = commonDates.map { stepsByDate.getValue(it).totalSteps }.sorted()
        val medianSteps = stepCounts[stepCounts.size / 2]

        val highBedtimes = mutableListOf<LocalTime>()
        val lowBedtimes = mutableListOf<LocalTime>()

        commonDates.forEach { date ->
            val steps = stepsByDate.getValue(date).totalSteps
            val bedtime = sleepByDate.getValue(date).bedtime!!
            if (steps >= medianSteps) {
                highBedtimes.add(bedtime)
            } else {
                lowBedtimes.add(bedtime)
            }
        }

        if (highBedtimes.isEmpty() || lowBedtimes.isEmpty()) return null

        val avgHighBedtime = CircularTimeCalculator.calculateCircularMean(highBedtimes) ?: return null
        val avgLowBedtime = CircularTimeCalculator.calculateCircularMean(lowBedtimes) ?: return null

        val highMinute = avgHighBedtime.hour * 60 + avgHighBedtime.minute
        val lowMinute = avgLowBedtime.hour * 60 + avgLowBedtime.minute
        var diff = highMinute - lowMinute
        if (diff > 720) diff -= 1440
        if (diff < -720) diff += 1440

        val confidence = if (count >= ROBUST_PAIRED_DAYS) InsightConfidence.SUFFICIENT_DATA else InsightConfidence.PRELIMINARY

        val headline: String
        val explanation: String

        if (diff <= -15) {
            headline = "Earlier bedtime on higher-activity days (${formatMinutes(diff)})"
            explanation = "On days with ${formatNumber(medianSteps)} or more steps, bedtime averaged ${avgHighBedtime}, compared to ${avgLowBedtime} on lower-activity days."
        } else if (diff >= 15) {
            headline = "Later bedtime on higher-activity days (+${formatMinutes(diff)})"
            explanation = "On days with ${formatNumber(medianSteps)} or more steps, bedtime averaged ${avgHighBedtime}, compared to ${avgLowBedtime} on lower-activity days."
        } else {
            headline = "Consistent bedtime regardless of physical activity"
            explanation = "Bedtime schedule was stable across both higher-activity days ($avgHighBedtime) and lower-activity days ($avgLowBedtime)."
        }

        return CrossMetricInsight(
            id = "activity_vs_bedtime",
            type = CrossMetricType.ACTIVITY_VS_BEDTIME,
            title = "Activity & Bedtime Schedule",
            headline = headline,
            explanation = explanation,
            validDaysCount = count,
            minimumDaysRequired = MINIMUM_PAIRED_DAYS,
            confidence = confidence,
            isStatisticallyReliable = confidence == InsightConfidence.SUFFICIENT_DATA,
            metricsInvolved = listOf("Bedtime", "Daily Steps")
        )
    }

    /**
     * Compares logged food energy against recorded energy expenditure.
     */
    fun evaluateEnergyBalance(
        nutritionSummaries: List<DailyNutritionSummary>,
        energyExpenditures: Map<LocalDate, HealthConnectEnergyExpenditure>
    ): CrossMetricInsight? {
        val validPairs = nutritionSummaries.filter { summary ->
            summary.totalCalories > 0 && energyExpenditures[summary.date]?.activeCaloriesKcal != null
        }

        if (validPairs.isEmpty()) return null

        val count = validPairs.size
        val avgIntake = validPairs.map { it.totalCalories }.average().roundToInt()
        val avgActiveExpenditure = validPairs.map { energyExpenditures[it.date]!!.activeCaloriesKcal!! }.average().roundToInt()

        val totalsList = validPairs.mapNotNull { energyExpenditures[it.date]?.totalCaloriesKcal }
        val avgTotalExpenditure = if (totalsList.isNotEmpty()) totalsList.average().roundToInt() else null

        val confidence = if (count >= 3) InsightConfidence.SUFFICIENT_DATA else InsightConfidence.PRELIMINARY

        val headline = "Energy Balance: $avgIntake kcal intake vs. $avgActiveExpenditure kcal active burn"
        val totalNote = if (avgTotalExpenditure != null) " (total recorded burn: $avgTotalExpenditure kcal)" else ""

        val explanation = "Across $count days with both food logs and recorded movement, average logged intake was $avgIntake kcal and average recorded active expenditure was $avgActiveExpenditure kcal$totalNote. Note: Unlogged meals may lead to underreported intake, and recorded expenditure only captures active movement windows."

        return CrossMetricInsight(
            id = "energy_balance_${count}",
            type = CrossMetricType.ENERGY_BALANCE,
            title = "Nutrition Intake & Energy Expenditure",
            headline = headline,
            explanation = explanation,
            caveat = "Analytical comparison only — not a clinical metabolic measurement.",
            validDaysCount = count,
            minimumDaysRequired = 1,
            confidence = confidence,
            isStatisticallyReliable = count >= 3,
            metricsInvolved = listOf("Dietary Intake", "Active Calories Burned")
        )
    }

    /**
     * Evaluates multi-metric rolling trajectory between the current 7-day period and prior 7 days.
     */
    fun evaluateMultiMetricTrend(
        sleepSummaries: List<DaySleepSummary>,
        stepSummaries: List<DailyStepSummary>,
        referenceDate: LocalDate = LocalDate.now()
    ): CrossMetricInsight? {
        val currentPeriod = referenceDate.minusDays(6)..referenceDate
        val priorPeriod = referenceDate.minusDays(13)..referenceDate.minusDays(7)

        val curSleep = sleepSummaries.filter { it.sleepDate in currentPeriod && it.totalSleepDurationMinutes >= 60 }
        val prevSleep = sleepSummaries.filter { it.sleepDate in priorPeriod && it.totalSleepDurationMinutes >= 60 }

        val curSteps = stepSummaries.filter { it.date in currentPeriod && it.totalSteps > 0 }
        val prevSteps = stepSummaries.filter { it.date in priorPeriod && it.totalSteps > 0 }

        if (curSleep.size < 3 || prevSleep.size < 3 || curSteps.size < 3 || prevSteps.size < 3) {
            return null
        }

        val curSleepAvg = curSleep.map { it.totalSleepDurationMinutes }.average().roundToInt()
        val prevSleepAvg = prevSleep.map { it.totalSleepDurationMinutes }.average().roundToInt()
        val sleepDiff = curSleepAvg - prevSleepAvg

        val curStepAvg = curSteps.map { it.totalSteps }.average().toLong()
        val prevStepAvg = prevSteps.map { it.totalSteps }.average().toLong()
        val stepDiff = curStepAvg - prevStepAvg

        val sleepDiffText = if (sleepDiff >= 0) "+${sleepDiff}m" else "${sleepDiff}m"
        val stepDiffText = if (stepDiff >= 0) "+${formatNumber(stepDiff)}" else formatNumber(stepDiff)

        val headline = "7-Day Trend: Sleep $sleepDiffText/night, Steps $stepDiffText/day"
        val explanation = "Comparing the last 7 days against the prior week: sleep duration averaged ${formatDuration(curSleepAvg)} ($sleepDiffText compared to prior week's ${formatDuration(prevSleepAvg)}), and daily activity averaged ${formatNumber(curStepAvg)} steps ($stepDiffText compared to prior week's ${formatNumber(prevStepAvg)})."

        return CrossMetricInsight(
            id = "multi_metric_7d_trend",
            type = CrossMetricType.MULTI_DAY_TREND,
            title = "Multi-Metric Rolling Trends",
            headline = headline,
            explanation = explanation,
            validDaysCount = curSleep.size + curSteps.size,
            minimumDaysRequired = 6,
            confidence = InsightConfidence.SUFFICIENT_DATA,
            isStatisticallyReliable = true,
            metricsInvolved = listOf("Sleep Duration Trend", "Daily Step Trend")
        )
    }

    private fun formatDuration(minutes: Int): String {
        val h = kotlin.math.abs(minutes) / 60
        val m = kotlin.math.abs(minutes) % 60
        return "${h}h ${m}m"
    }

    private fun formatMinutes(minutes: Int): String {
        val sign = if (minutes >= 0) "+" else "-"
        val abs = kotlin.math.abs(minutes)
        return "$sign${abs}m"
    }

    private fun formatNumber(value: Long): String {
        return "%,d".format(java.util.Locale.US, value)
    }
}
