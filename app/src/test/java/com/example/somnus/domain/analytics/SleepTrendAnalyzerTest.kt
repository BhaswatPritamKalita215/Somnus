package com.example.somnus.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class SleepTrendAnalyzerTest {

    private fun createMetrics(
        nightsTracked: Int,
        totalDays: Int = 7,
        avgDuration: Double = 480.0,
        bedtimeVar: Double = 15.0,
        avgSol: Double? = 15.0
    ): PeriodSleepMetrics {
        return PeriodSleepMetrics(
            averageDurationMinutes = avgDuration,
            averageTibMinutes = avgDuration + 20.0,
            typicalBedtime = LocalTime.of(23, 0),
            typicalWakeTime = LocalTime.of(7, 0),
            bedtimeVariabilityMinutes = bedtimeVar,
            wakeTimeVariabilityMinutes = 15.0,
            durationVariabilityMinutes = 20.0,
            averageSolMinutes = avgSol,
            totalNightsTracked = nightsTracked,
            totalDaysInRange = totalDays,
            coveragePercentage = (nightsTracked.toDouble() / totalDays) * 100.0
        )
    }

    @Test
    fun `comparison with sufficient data in both periods is marked reliable`() {
        val current = createMetrics(nightsTracked = 7, avgDuration = 480.0) // 8h
        val baseline = createMetrics(nightsTracked = 6, avgDuration = 420.0) // 7h

        val comparisons = SleepTrendAnalyzer.comparePeriods(current, baseline, "Last 7 Days")

        val durationComp = comparisons.find { it.metricName == "Average Sleep Duration" }
        assertTrue(durationComp != null)
        assertTrue(durationComp!!.isComparisonReliable)
        // 480 - 420 = +60 minutes
        assertEquals(60.0, durationComp.absoluteDifference!!, 0.01)
        assertEquals(14.3, durationComp.percentageDifference!!, 0.1)
        assertEquals(ComparisonDirection.INCREASED, durationComp.direction)
        assertEquals(7, durationComp.currentValidNights)
        assertEquals(6, durationComp.previousValidNights)
    }

    @Test
    fun `comparison with insufficient data in current period is marked unreliable`() {
        val current = createMetrics(nightsTracked = 2, avgDuration = 480.0) // only 2 nights
        val baseline = createMetrics(nightsTracked = 6, avgDuration = 450.0)

        val comparisons = SleepTrendAnalyzer.comparePeriods(current, baseline, "Last 7 Days")

        val durationComp = comparisons.find { it.metricName == "Average Sleep Duration" }
        assertTrue(durationComp != null)
        assertFalse(durationComp!!.isComparisonReliable)
        assertTrue(durationComp.qualificationNote.contains("Preliminary comparison"))
    }

    @Test
    fun `comparison with insufficient data in baseline period is marked unreliable`() {
        val current = createMetrics(nightsTracked = 6, avgDuration = 480.0)
        val baseline = createMetrics(nightsTracked = 1, avgDuration = 420.0) // only 1 night

        val comparisons = SleepTrendAnalyzer.comparePeriods(current, baseline, "Last 7 Days")

        val durationComp = comparisons.find { it.metricName == "Average Sleep Duration" }
        assertTrue(durationComp != null)
        assertFalse(durationComp!!.isComparisonReliable)
    }
}
