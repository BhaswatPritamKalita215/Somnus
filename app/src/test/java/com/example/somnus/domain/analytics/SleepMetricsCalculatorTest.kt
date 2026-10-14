package com.example.somnus.domain.analytics

import com.example.somnus.domain.model.SessionSource
import com.example.somnus.domain.model.SessionStatus
import com.example.somnus.domain.model.SleepSession
import com.example.somnus.domain.model.SolMeasurementMethod
import com.example.somnus.domain.model.SolRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class SleepMetricsCalculatorTest {

    private val zoneId = ZoneId.of("UTC")

    private fun instant(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zoneId).toInstant()
    }

    private fun createSession(
        id: String,
        startTime: Instant,
        endTime: Instant,
        source: SessionSource = SessionSource.MANUAL
    ): SleepSession {
        return SleepSession(
            id = id,
            startTime = startTime,
            endTime = endTime,
            zoneId = zoneId,
            status = SessionStatus.COMPLETED,
            source = source
        )
    }

    @Test
    fun `determineSleepDate assigns early morning starts to prior calendar evening`() {
        // Session starting at 23:00 on Oct 10 -> sleep date Oct 10
        val eveningSession = createSession("1", instant(2026, 10, 10, 23, 0), instant(2026, 10, 11, 7, 0))
        assertEquals(LocalDate.of(2026, 10, 10), SleepMetricsCalculator.determineSleepDate(eveningSession))

        // Session starting at 02:30 AM on Oct 11 -> sleep date Oct 10
        val lateNightSession = createSession("2", instant(2026, 10, 11, 2, 30), instant(2026, 10, 11, 8, 30))
        assertEquals(LocalDate.of(2026, 10, 10), SleepMetricsCalculator.determineSleepDate(lateNightSession))

        // Daytime nap starting at 14:00 on Oct 11 -> sleep date Oct 11
        val afternoonNap = createSession("3", instant(2026, 10, 11, 14, 0), instant(2026, 10, 11, 15, 0))
        assertEquals(LocalDate.of(2026, 10, 11), SleepMetricsCalculator.determineSleepDate(afternoonNap))
    }

    @Test
    fun `mergeIntervals merges overlapping intervals with zero double counting`() {
        // Interval A: 0 to 480 (8 hours)
        // Interval B: 30 to 270 (completely inside Interval A)
        val intervals = listOf(
            0L to 480_000L,
            30_000L to 270_000L
        )

        val mergedMillis = SleepMetricsCalculator.mergeIntervals(intervals)
        assertEquals(480_000L, mergedMillis)
    }

    @Test
    fun `overlapping sessions on the same night are aggregated with zero double-counting`() {
        // Night of Oct 10:
        // Session A: 23:00 to 07:00 (480 mins = 8h)
        // Session B (wearable import): 23:30 to 04:00 (270 mins) - inside Session A
        val sessionA = createSession("A", instant(2026, 10, 10, 23, 0), instant(2026, 10, 11, 7, 0), SessionSource.MANUAL)
        val sessionB = createSession("B", instant(2026, 10, 10, 23, 30), instant(2026, 10, 11, 4, 0), SessionSource.WEARABLE_IMPORT)

        val summaries = SleepMetricsCalculator.aggregateDailySummaries(
            sessions = listOf(sessionA, sessionB),
            solRecords = emptyList()
        )

        assertEquals(1, summaries.size)
        val daySummary = summaries.first()

        assertEquals(LocalDate.of(2026, 10, 10), daySummary.sleepDate)
        // Total duration must be exactly 480 mins, never 750 mins!
        assertEquals(480L, daySummary.totalSleepDurationMinutes)
        assertEquals(2, daySummary.sessionCount)
        assertTrue(daySummary.sources.contains(SessionSource.MANUAL))
        assertTrue(daySummary.sources.contains(SessionSource.WEARABLE_IMPORT))
        assertTrue(daySummary.hasConflictingOverlaps)
    }

    @Test
    fun `sol records match their reporting date accurately`() {
        val session = createSession("1", instant(2026, 10, 10, 23, 0), instant(2026, 10, 11, 7, 0))
        val sol = SolRecord(
            id = "sol-1",
            reportingDate = LocalDate.of(2026, 10, 10),
            latencyMinutes = 25,
            method = SolMeasurementMethod.SELF_REPORTED
        )

        val summaries = SleepMetricsCalculator.aggregateDailySummaries(
            sessions = listOf(session),
            solRecords = listOf(sol)
        )

        assertEquals(1, summaries.size)
        assertEquals(25, summaries.first().solMinutes)
    }

    @Test
    fun `calculatePeriodMetrics aggregates accurately across tracked nights`() {
        // 3 tracked nights: 420m (7h), 480m (8h), 540m (9h) -> Mean = 480.0
        val s1 = createSession("1", instant(2026, 10, 1, 23, 0), instant(2026, 10, 2, 6, 0)) // 420m
        val s2 = createSession("2", instant(2026, 10, 2, 23, 0), instant(2026, 10, 3, 7, 0)) // 480m
        val s3 = createSession("3", instant(2026, 10, 3, 23, 0), instant(2026, 10, 4, 8, 0)) // 540m

        val summaries = SleepMetricsCalculator.aggregateDailySummaries(
            sessions = listOf(s1, s2, s3)
        )

        val range = SleepDateRange(
            startDate = LocalDate.of(2026, 10, 1),
            endDate = LocalDate.of(2026, 10, 4), // 4 total days, 3 tracked
            label = "4 Days",
            option = DateRangeOption.CUSTOM
        )

        val metrics = SleepMetricsCalculator.calculatePeriodMetrics(summaries, range)

        assertEquals(4, metrics.totalDaysInRange)
        assertEquals(3, metrics.totalNightsTracked)
        assertEquals(480.0, metrics.averageDurationMinutes!!, 0.01)
        assertEquals(75.0, metrics.coveragePercentage, 0.01)
        assertTrue(metrics.hasSufficientData)
        assertNotNull(metrics.typicalBedtime)
        assertNotNull(metrics.typicalWakeTime)
    }
}
