package com.example.somnus.domain.analytics

import com.example.somnus.domain.model.SessionSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SleepDataQualityAnalyzerTest {

    private fun createDay(
        date: LocalDate,
        hasData: Boolean,
        sources: Set<SessionSource> = if (hasData) setOf(SessionSource.MANUAL) else emptySet(),
        isConflicted: Boolean = false
    ): DaySleepSummary {
        return DaySleepSummary(
            sleepDate = date,
            totalSleepDurationMinutes = if (hasData) 480L else 0L,
            totalTimeInBedMinutes = if (hasData) 480L else 0L,
            bedtime = null,
            wakeTime = null,
            sources = sources,
            sessionCount = if (hasData) 1 else 0,
            hasConflictingOverlaps = isConflicted
        )
    }

    @Test
    fun `quality levels are properly assigned based on coverage percentage`() {
        val range = SleepDateRange(
            startDate = LocalDate.of(2026, 10, 1),
            endDate = LocalDate.of(2026, 10, 7),
            label = "7 Days"
        )

        // 7 of 7 recorded = 100% -> EXCELLENT
        val days100 = (1..7).map { createDay(LocalDate.of(2026, 10, it), hasData = true) }
        val quality100 = SleepDataQualityAnalyzer.analyzeDataQuality(days100, range)
        assertEquals(DataQualityLevel.EXCELLENT, quality100.qualityLevel)
        assertEquals(7, quality100.nightsWithUsableData)
        assertEquals(0, quality100.missingNightsCount)

        // 5 of 7 recorded = 71.4% -> ADEQUATE
        val days71 = (1..7).map { createDay(LocalDate.of(2026, 10, it), hasData = it <= 5) }
        val quality71 = SleepDataQualityAnalyzer.analyzeDataQuality(days71, range)
        assertEquals(DataQualityLevel.ADEQUATE, quality71.qualityLevel)
        assertEquals(5, quality71.nightsWithUsableData)
        assertEquals(2, quality71.missingNightsCount)

        // 3 of 7 recorded = 42.8% -> SPARSE
        val days42 = (1..7).map { createDay(LocalDate.of(2026, 10, it), hasData = it <= 3) }
        val quality42 = SleepDataQualityAnalyzer.analyzeDataQuality(days42, range)
        assertEquals(DataQualityLevel.SPARSE, quality42.qualityLevel)

        // 1 of 7 recorded = 14.3% -> INSUFFICIENT
        val days14 = (1..7).map { createDay(LocalDate.of(2026, 10, it), hasData = it == 1) }
        val quality14 = SleepDataQualityAnalyzer.analyzeDataQuality(days14, range)
        assertEquals(DataQualityLevel.INSUFFICIENT, quality14.qualityLevel)
    }

    @Test
    fun `conflicted nights and source breakdown are accurately identified`() {
        val d1 = createDay(LocalDate.of(2026, 10, 1), hasData = true, sources = setOf(SessionSource.MANUAL))
        val d2 = createDay(LocalDate.of(2026, 10, 2), hasData = false)
        val d3 = createDay(LocalDate.of(2026, 10, 3), hasData = true, sources = setOf(SessionSource.MANUAL, SessionSource.WEARABLE_IMPORT), isConflicted = true)
        val d4 = createDay(LocalDate.of(2026, 10, 4), hasData = false)

        val range = SleepDateRange(
            startDate = LocalDate.of(2026, 10, 1),
            endDate = LocalDate.of(2026, 10, 4),
            label = "4 Days"
        )

        val quality = SleepDataQualityAnalyzer.analyzeDataQuality(listOf(d1, d2, d3, d4), range)

        assertEquals(4, quality.totalDays)
        assertEquals(2, quality.nightsWithUsableData)
        assertEquals(2, quality.missingNightsCount)
        assertEquals(1, quality.conflictedNightsCount)
        assertEquals(2, quality.sourceBreakdown[SessionSource.MANUAL])
        assertEquals(1, quality.sourceBreakdown[SessionSource.WEARABLE_IMPORT])
    }
}
