package com.example.somnus.domain.analytics

import com.example.somnus.domain.model.SessionSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class DateRangeOption(val label: String, val days: Int) {
    LAST_7_DAYS("Last 7 Days", 7),
    LAST_30_DAYS("Last 30 Days", 30),
    LAST_90_DAYS("Last 90 Days", 90),
    CUSTOM("Custom Range", 0)
}

data class SleepDateRange(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val label: String,
    val option: DateRangeOption = DateRangeOption.LAST_7_DAYS
) {
    val totalDays: Int
        get() = (java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1).toInt()

    fun getPrecedingComparisonRange(): SleepDateRange {
        val days = totalDays
        val prevEnd = startDate.minusDays(1)
        val prevStart = prevEnd.minusDays(days.toLong() - 1)
        return SleepDateRange(
            startDate = prevStart,
            endDate = prevEnd,
            label = "Prior $days Days",
            option = option
        )
    }
}

/**
 * Aggregated summary for a single clinical sleep day.
 *
 * Sleep-day convention: An overnight session starting in the evening/night belongs to the
 * sleep day of that start date (or the waking date, applied uniformly).
 */
data class DaySleepSummary(
    val sleepDate: LocalDate,
    val totalSleepDurationMinutes: Long,
    val totalTimeInBedMinutes: Long,
    val bedtime: LocalTime?,
    val wakeTime: LocalTime?,
    val solMinutes: Int? = null,
    val isNap: Boolean = false,
    val sources: Set<SessionSource> = setOf(SessionSource.MANUAL),
    val sessionCount: Int = 1,
    val hasConflictingOverlaps: Boolean = false,
    val notes: String? = null
)

/**
 * Aggregated summary statistics across a defined time period.
 */
data class PeriodSleepMetrics(
    val averageDurationMinutes: Double?,
    val averageTibMinutes: Double?,
    val typicalBedtime: LocalTime?,
    val typicalWakeTime: LocalTime?,
    val bedtimeVariabilityMinutes: Double?,
    val wakeTimeVariabilityMinutes: Double?,
    val durationVariabilityMinutes: Double?,
    val averageSolMinutes: Double?,
    val totalNightsTracked: Int,
    val totalDaysInRange: Int,
    val coveragePercentage: Double
) {
    val hasSufficientData: Boolean
        get() = totalNightsTracked >= 3 && coveragePercentage >= 40.0
}

enum class ComparisonDirection {
    INCREASED,
    DECREASED,
    UNCHANGED,
    INSUFFICIENT_DATA
}

/**
 * Comparison between the current analysis period and a preceding baseline period.
 */
data class MetricComparison(
    val metricName: String,
    val currentValue: Double?,
    val previousValue: Double?,
    val unit: String,
    val absoluteDifference: Double?,
    val percentageDifference: Double?,
    val currentValidNights: Int,
    val previousValidNights: Int,
    val direction: ComparisonDirection,
    val isComparisonReliable: Boolean,
    val qualificationNote: String
)

enum class DataQualityLevel {
    EXCELLENT,
    ADEQUATE,
    SPARSE,
    INSUFFICIENT
}

data class SleepDataQuality(
    val totalDays: Int,
    val nightsWithUsableData: Int,
    val missingNightsCount: Int,
    val conflictedNightsCount: Int,
    val sourceBreakdown: Map<SessionSource, Int>,
    val coveragePercentage: Double,
    val qualityLevel: DataQualityLevel
)

enum class InsightCategory {
    DURATION,
    CONSISTENCY,
    LATENCY,
    DATA_COVERAGE
}

enum class EvidenceLevel {
    ROBUST,
    MODERATE,
    PRELIMINARY
}

/**
 * Deterministic, transparent rules-based insight.
 * Traceable strictly to underlying persisted records.
 */
data class SleepInsight(
    val id: String = UUID.randomUUID().toString(),
    val category: InsightCategory,
    val title: String,
    val observation: String,
    val explanation: String,
    val evidenceLevel: EvidenceLevel,
    val comparisonPeriod: String? = null
)

/**
 * Full analytics report for the currently selected date range.
 */
data class AnalyticsReport(
    val selectedRange: SleepDateRange,
    val dailySummaries: List<DaySleepSummary>,
    val periodMetrics: PeriodSleepMetrics,
    val comparisons: List<MetricComparison>,
    val dataQuality: SleepDataQuality,
    val insights: List<SleepInsight>,
    val latestPsqiGlobalScore: Int?,
    val generatedAt: Instant = Instant.now()
)
