package com.example.somnus.domain.analytics

import java.time.LocalTime
import kotlin.math.abs

/**
 * Pure Kotlin trend comparison analyzer.
 *
 * Evaluates changes between the current selected date range and the preceding baseline period.
 * Strictly verifies observation sufficiency to prevent misleading comparisons.
 */
object SleepTrendAnalyzer {

    /** Minimum number of tracked nights required in BOTH periods to consider a comparison reliable. */
    const val MIN_OBSERVATIONS_FOR_RELIABLE_COMPARISON = 3

    /** Minimum coverage ratio between periods (e.g., comparing 6 nights with 2 nights is flagged as unequal coverage). */
    const val MIN_COVERAGE_EQUIVALENCE_RATIO = 0.5

    fun comparePeriods(
        currentMetrics: PeriodSleepMetrics,
        previousMetrics: PeriodSleepMetrics,
        periodLabel: String
    ): List<MetricComparison> {
        val comparisons = mutableListOf<MetricComparison>()

        val currNights = currentMetrics.totalNightsTracked
        val prevNights = previousMetrics.totalNightsTracked

        val hasBasicData = currNights > 0 && prevNights > 0
        val isReliable = currNights >= MIN_OBSERVATIONS_FOR_RELIABLE_COMPARISON &&
                prevNights >= MIN_OBSERVATIONS_FOR_RELIABLE_COMPARISON &&
                (minOf(currNights, prevNights).toDouble() / maxOf(currNights, prevNights)) >= MIN_COVERAGE_EQUIVALENCE_RATIO

        // 1. Duration Comparison
        comparisons.add(
            createNumericalComparison(
                metricName = "Average Sleep Duration",
                curr = currentMetrics.averageDurationMinutes,
                prev = previousMetrics.averageDurationMinutes,
                unit = "minutes",
                currNights = currNights,
                prevNights = prevNights,
                isReliable = isReliable,
                thresholdMinutes = 15.0 // changes under 15 min are considered unchanged
            )
        )

        // 2. Bedtime Consistency Comparison
        comparisons.add(
            createNumericalComparison(
                metricName = "Bedtime Consistency",
                curr = currentMetrics.bedtimeVariabilityMinutes,
                prev = previousMetrics.bedtimeVariabilityMinutes,
                unit = "minutes std dev",
                currNights = currNights,
                prevNights = prevNights,
                isReliable = isReliable,
                thresholdMinutes = 10.0,
                invertDirection = true // lower variability is improvement
            )
        )

        // 3. Sleep Onset Latency Comparison (if available in both)
        if (currentMetrics.averageSolMinutes != null && previousMetrics.averageSolMinutes != null) {
            comparisons.add(
                createNumericalComparison(
                    metricName = "Average Sleep Onset Latency",
                    curr = currentMetrics.averageSolMinutes,
                    prev = previousMetrics.averageSolMinutes,
                    unit = "minutes",
                    currNights = currNights,
                    prevNights = prevNights,
                    isReliable = isReliable,
                    thresholdMinutes = 5.0,
                    invertDirection = true
                )
            )
        }

        return comparisons
    }

    private fun createNumericalComparison(
        metricName: String,
        curr: Double?,
        prev: Double?,
        unit: String,
        currNights: Int,
        prevNights: Int,
        isReliable: Boolean,
        thresholdMinutes: Double = 1.0,
        invertDirection: Boolean = false
    ): MetricComparison {
        if (curr == null || prev == null) {
            return MetricComparison(
                metricName = metricName,
                currentValue = curr,
                previousValue = prev,
                unit = unit,
                absoluteDifference = null,
                percentageDifference = null,
                currentValidNights = currNights,
                previousValidNights = prevNights,
                direction = ComparisonDirection.INSUFFICIENT_DATA,
                isComparisonReliable = false,
                qualificationNote = "Insufficient observations in one or both comparison periods."
            )
        }

        val diff = curr - prev
        val pct = if (prev > 0.0) ((diff / prev) * 100.0) else null

        val direction = if (abs(diff) < thresholdMinutes) {
            ComparisonDirection.UNCHANGED
        } else if (diff > 0) {
            if (invertDirection) ComparisonDirection.INCREASED else ComparisonDirection.INCREASED
        } else {
            ComparisonDirection.DECREASED
        }

        val note = if (!isReliable) {
            "Preliminary comparison: based on unequal coverage ($currNights nights vs $prevNights nights)."
        } else {
            "Sufficient observations in both periods ($currNights nights vs $prevNights nights)."
        }

        return MetricComparison(
            metricName = metricName,
            currentValue = Math.round(curr * 10.0) / 10.0,
            previousValue = Math.round(prev * 10.0) / 10.0,
            unit = unit,
            absoluteDifference = Math.round(diff * 10.0) / 10.0,
            percentageDifference = pct?.let { Math.round(it * 10.0) / 10.0 },
            currentValidNights = currNights,
            previousValidNights = prevNights,
            direction = direction,
            isComparisonReliable = isReliable,
            qualificationNote = note
        )
    }
}
