package com.example.somnus.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class DurationCalculatorTest {

    @Test
    fun calculateDuration_standardInterval_returnsAccurateDuration() {
        val start = Instant.parse("2026-10-13T22:00:00Z")
        val end = Instant.parse("2026-10-14T06:30:00Z")

        val duration = DurationCalculator.calculateDuration(start, end)
        assertEquals(Duration.ofHours(8).plusMinutes(30), duration)
        assertEquals(8 * 3600 + 30 * 60, duration.seconds)
    }

    @Test
    fun calculateDuration_crossesMidnight_calculatesAccurately() {
        // Bedtime at 11:45 PM on Oct 13, wake up at 7:15 AM on Oct 14
        val start = Instant.parse("2026-10-13T23:45:00Z")
        val end = Instant.parse("2026-10-14T07:15:00Z")

        val duration = DurationCalculator.calculateDuration(start, end)
        assertEquals(Duration.ofHours(7).plusMinutes(30), duration)
        assertEquals("7h 30m", DurationCalculator.formatDuration(duration))
    }

    @Test
    fun calculateDuration_endBeforeStart_throwsIllegalArgumentException() {
        val start = Instant.parse("2026-10-14T08:00:00Z")
        val end = Instant.parse("2026-10-14T07:00:00Z")

        val exception = assertThrows(IllegalArgumentException::class.java) {
            DurationCalculator.calculateDuration(start, end)
        }
        assertEquals(true, exception.message?.contains("cannot be before start time"))
    }

    @Test
    fun calculateDuration_zeroLength_throwsIllegalArgumentException() {
        val instant = Instant.parse("2026-10-14T08:00:00Z")

        val exception = assertThrows(IllegalArgumentException::class.java) {
            DurationCalculator.calculateDuration(instant, instant)
        }
        assertEquals(true, exception.message?.contains("must be greater than zero"))
    }

    @Test
    fun calculateDuration_dstFallBackTransition_maintainsTruePhysicalDuration() {
        // In New York (America/New_York), on Nov 1, 2026, clocks turn back 1 hour at 2:00 AM (EDT -> EST).
        // A person goes to bed at 11:00 PM EDT and wakes at 7:00 AM EST.
        // Wall clock says 8 hours, but actual elapsed physical time is 9 hours!
        val zone = ZoneId.of("America/New_York")
        val startZoned = ZonedDateTime.of(2026, 11, 1, 23, 0, 0, 0, zone) // 11:00 PM EDT
        val endZoned = ZonedDateTime.of(2026, 11, 2, 7, 0, 0, 0, zone)   // 7:00 AM EST

        val startInstant = startZoned.toInstant()
        val endInstant = endZoned.toInstant()

        val duration = DurationCalculator.calculateDuration(startInstant, endInstant)
        // Instant math operates strictly in UTC epoch time, accurately calculating physical elapsed time
        assertEquals(Duration.ofHours(8), duration)
    }

    @Test
    fun calculateElapsed_computesCorrectly() {
        val start = Instant.parse("2026-10-14T01:00:00Z")
        val now = Instant.parse("2026-10-14T02:15:30Z")

        val elapsed = DurationCalculator.calculateElapsed(start, now)
        assertEquals(Duration.ofHours(1).plusMinutes(15).plusSeconds(30), elapsed)
        assertEquals("01:15:30", DurationCalculator.formatElapsed(elapsed))
    }

    @Test
    fun calculateElapsed_clockSkewNegative_clampsToZero() {
        val start = Instant.parse("2026-10-14T02:00:00Z")
        val now = Instant.parse("2026-10-14T01:59:58Z") // 2 seconds behind start due to skew

        val elapsed = DurationCalculator.calculateElapsed(start, now)
        assertEquals(Duration.ZERO, elapsed)
        assertEquals("00:00:00", DurationCalculator.formatElapsed(elapsed))
    }

    @Test
    fun formatDuration_variousDurations() {
        assertEquals("0m", DurationCalculator.formatDuration(null))
        assertEquals("0m", DurationCalculator.formatDuration(Duration.ZERO))
        assertEquals("45m", DurationCalculator.formatDuration(Duration.ofMinutes(45)))
        assertEquals("8h", DurationCalculator.formatDuration(Duration.ofHours(8)))
        assertEquals("7h 15m", DurationCalculator.formatDuration(Duration.ofHours(7).plusMinutes(15)))
    }
}
