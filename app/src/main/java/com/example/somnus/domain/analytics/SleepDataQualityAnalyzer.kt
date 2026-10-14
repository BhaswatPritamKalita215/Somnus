package com.example.somnus.domain.analytics

import com.example.somnus.domain.model.SessionSource

/**
 * Pure Kotlin data-quality analyzer evaluating tracking coverage and source provenance.
 *
 * Ensures the user is transparently informed of data completeness without
 * framing gaps as personal failure.
 */
object SleepDataQualityAnalyzer {

    fun analyzeDataQuality(
        dailySummaries: List<DaySleepSummary>,
        dateRange: SleepDateRange
    ): SleepDataQuality {
        val totalDays = dateRange.totalDays
        val usableNights = dailySummaries.filter { it.totalSleepDurationMinutes > 0 }
        val usableCount = usableNights.size
        val missingCount = maxOf(0, totalDays - usableCount)
        val conflictedCount = dailySummaries.count { it.hasConflictingOverlaps }

        // Source breakdown
        val sourceCounts = mutableMapOf<SessionSource, Int>()
        for (summary in usableNights) {
            for (source in summary.sources) {
                sourceCounts[source] = (sourceCounts[source] ?: 0) + 1
            }
        }

        val coveragePct = if (totalDays > 0) {
            Math.round((usableCount.toDouble() / totalDays) * 1000.0) / 10.0
        } else {
            0.0
        }

        val qualityLevel = when {
            usableCount < 2 || coveragePct < 25.0 -> DataQualityLevel.INSUFFICIENT
            coveragePct < 50.0 -> DataQualityLevel.SPARSE
            coveragePct < 80.0 -> DataQualityLevel.ADEQUATE
            else -> DataQualityLevel.EXCELLENT
        }

        return SleepDataQuality(
            totalDays = totalDays,
            nightsWithUsableData = usableCount,
            missingNightsCount = missingCount,
            conflictedNightsCount = conflictedCount,
            sourceBreakdown = sourceCounts,
            coveragePercentage = coveragePct,
            qualityLevel = qualityLevel
        )
    }
}
