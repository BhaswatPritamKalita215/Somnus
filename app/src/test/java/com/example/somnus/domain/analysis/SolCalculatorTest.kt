package com.example.somnus.domain.analysis

import com.example.somnus.domain.model.SolMeasurementMethod
import com.example.somnus.domain.model.SolQualityFlag
import com.example.somnus.domain.model.SolRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SolCalculatorTest {

    @Test
    fun calculateSolMinutes_validTimes_returnsCorrectDuration() {
        val attempt = Instant.parse("2026-10-14T23:15:00Z")
        val onset = Instant.parse("2026-10-14T23:45:00Z")

        val result = SolCalculator.calculateSolMinutes(attempt, onset)

        assertTrue(result.isSuccess)
        assertEquals(30, result.getOrNull())
    }

    @Test
    fun calculateSolMinutes_crossesMidnight_returnsCorrectDuration() {
        val attempt = Instant.parse("2026-10-14T23:50:00Z")
        val onset = Instant.parse("2026-10-15T00:25:00Z")

        val result = SolCalculator.calculateSolMinutes(attempt, onset)

        assertTrue(result.isSuccess)
        assertEquals(35, result.getOrNull())
    }

    @Test
    fun calculateSolMinutes_onsetBeforeAttempt_returnsFailure() {
        val attempt = Instant.parse("2026-10-14T23:30:00Z")
        val onset = Instant.parse("2026-10-14T23:00:00Z")

        val result = SolCalculator.calculateSolMinutes(attempt, onset)

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertTrue(result.exceptionOrNull()?.message?.contains("before attempt-to-sleep") == true)
    }

    @Test
    fun calculateSolMinutes_exceedsMaxPhysiologicalLimit_returnsFailure() {
        val attempt = Instant.parse("2026-10-14T20:00:00Z")
        val onset = attempt.plus(Duration.ofMinutes(1441)) // 24 hours + 1 min

        val result = SolCalculator.calculateSolMinutes(attempt, onset)

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message?.contains("exceeds physiological limit") == true)
    }

    @Test
    fun evaluateQualityFlags_outlierAndMissingAttempt_flagsAccurately() {
        val flags = SolCalculator.evaluateQualityFlags(
            latencyMinutes = 135,
            hasExplicitTimestamps = false,
            isManuallyEdited = true
        )

        assertTrue(flags.contains(SolQualityFlag.OUTLIER_EXCESSIVE_LATENCY))
        assertTrue(flags.contains(SolQualityFlag.MISSING_SLEEP_ATTEMPT))
        assertTrue(flags.contains(SolQualityFlag.EDITED_MANUALLY))
    }

    @Test
    fun calculateSummary_emptyRecords_returnsNullMetricsAndAllMissingNights() {
        val summary = SolCalculator.calculateSummary(emptyList(), 7, "Past 7 Days")

        assertEquals(0, summary.recordedNightsCount)
        assertEquals(7, summary.missingNightsCount)
        assertNull(summary.averageLatencyMinutes)
        assertNull(summary.medianLatencyMinutes)
        assertNull(summary.minLatencyMinutes)
        assertNull(summary.maxLatencyMinutes)
    }

    @Test
    fun calculateSummary_withRecords_computesCorrectMeanMedianAndMethodBreakdown() {
        val date1 = LocalDate.of(2026, 10, 10)
        val date2 = LocalDate.of(2026, 10, 11)
        val date3 = LocalDate.of(2026, 10, 12)
        val date4 = LocalDate.of(2026, 10, 13)

        val records = listOf(
            SolRecord(reportingDate = date1, latencyMinutes = 10, method = SolMeasurementMethod.SELF_REPORTED),
            SolRecord(reportingDate = date2, latencyMinutes = 20, method = SolMeasurementMethod.SELF_REPORTED),
            SolRecord(reportingDate = date3, latencyMinutes = 30, method = SolMeasurementMethod.ALGORITHM_ESTIMATE),
            SolRecord(reportingDate = date4, latencyMinutes = 40, method = SolMeasurementMethod.WEARABLE_IMPORT)
        )

        val summary = SolCalculator.calculateSummary(records, 7, "Past 7 Days")

        assertEquals(4, summary.recordedNightsCount)
        assertEquals(3, summary.missingNightsCount) // 7 - 4 = 3 missing nights
        assertEquals(25.0, summary.averageLatencyMinutes!!, 0.01) // (10+20+30+40)/4 = 25.0
        assertEquals(25.0, summary.medianLatencyMinutes!!, 0.01)  // (20+30)/2 = 25.0
        assertEquals(10, summary.minLatencyMinutes)
        assertEquals(40, summary.maxLatencyMinutes)
        assertEquals(2, summary.selfReportedCount)
        assertEquals(2, summary.importedOrEstimatedCount)
    }

    @Test
    fun calculateSummary_oddNumberOfRecords_computesExactMedian() {
        val records = listOf(
            SolRecord(reportingDate = LocalDate.now(), latencyMinutes = 15),
            SolRecord(reportingDate = LocalDate.now().minusDays(1), latencyMinutes = 5),
            SolRecord(reportingDate = LocalDate.now().minusDays(2), latencyMinutes = 45)
        )

        val summary = SolCalculator.calculateSummary(records, 3, "Window")

        assertEquals(15.0, summary.medianLatencyMinutes!!, 0.01)
    }
}
