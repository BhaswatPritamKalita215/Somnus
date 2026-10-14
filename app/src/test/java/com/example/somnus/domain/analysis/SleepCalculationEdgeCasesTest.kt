package com.example.somnus.domain.analysis

import com.example.somnus.domain.analytics.CircularTimeCalculator
import com.example.somnus.domain.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class SleepCalculationEdgeCasesTest {

    @Test
    fun dstSpringForward_calculatesExactPhysicalDuration_despiteWallClockHourSkip() {
        val zone = ZoneId.of("America/New_York")
        // In 2026, US Spring forward happens Sunday March 8 at 2:00 AM (jumps to 3:00 AM)
        val bedtimeLocal = ZonedDateTime.of(2026, 3, 7, 23, 0, 0, 0, zone)
        val waketimeLocal = ZonedDateTime.of(2026, 3, 8, 7, 0, 0, 0, zone)

        val bedtimeUtc = bedtimeLocal.toInstant()
        val waketimeUtc = waketimeLocal.toInstant()

        // Physical duration is 7 hours because clock jumped 1 hour forward!
        val physicalDuration = Duration.between(bedtimeUtc, waketimeUtc)
        assertEquals(Duration.ofHours(7), physicalDuration)

        // SessionValidator must accept this
        val validation = SessionValidator.validate(
            start = bedtimeUtc,
            end = waketimeUtc,
            status = SessionStatus.COMPLETED,
            now = waketimeUtc.plusSeconds(300)
        )
        assertTrue(validation is ValidationResult.Valid)
    }

    @Test
    fun dstFallBackward_calculatesExactPhysicalDuration_despiteWallClockHourRepeat() {
        val zone = ZoneId.of("America/New_York")
        // In 2026, US Fall backward happens Sunday Nov 1 at 2:00 AM (falls back to 1:00 AM)
        val bedtimeLocal = ZonedDateTime.of(2026, 10, 31, 23, 0, 0, 0, zone)
        val waketimeLocal = ZonedDateTime.of(2026, 11, 1, 7, 0, 0, 0, zone)

        val bedtimeUtc = bedtimeLocal.toInstant()
        val waketimeUtc = waketimeLocal.toInstant()

        // Physical duration is 9 hours because 1 hour was repeated!
        val physicalDuration = Duration.between(bedtimeUtc, waketimeUtc)
        assertEquals(Duration.ofHours(9), physicalDuration)

        val validation = SessionValidator.validate(
            start = bedtimeUtc,
            end = waketimeUtc,
            status = SessionStatus.COMPLETED,
            now = waketimeUtc.plusSeconds(300)
        )
        assertTrue(validation is ValidationResult.Valid)
    }

    @Test
    fun midnightCrossing_circularMeanCalculatedAccurately() {
        val t1 = LocalTime.of(23, 30) // 11:30 PM
        val t2 = LocalTime.of(0, 30)  // 12:30 AM (next morning)

        // Arithmetic average: (23*60+30 + 30)/2 = 720 minutes = 12:00 NOON (incorrect!)
        // Circular mean: 00:00 MIDNIGHT (correct!)
        val circularMean = CircularTimeCalculator.calculateCircularMean(listOf(t1, t2))
        assertEquals(LocalTime.of(0, 0), circularMean)

        val singleMean = CircularTimeCalculator.calculateCircularMean(listOf(t1))
        assertEquals(t1, singleMean)
    }

    @Test
    fun zeroDuration_isRejectedByValidator() {
        val now = Instant.now()
        val bedtime = now.minusSeconds(7200)
        val validation = SessionValidator.validate(bedtime, bedtime, SessionStatus.COMPLETED, now = now)
        assertTrue(validation is ValidationResult.Invalid)
        assertEquals("Session duration cannot be zero", (validation as ValidationResult.Invalid).reason)
    }

    @Test
    fun negativeDuration_isRejectedByValidator() {
        val now = Instant.now()
        val bedtime = now.minusSeconds(3600)
        val waketime = now.minusSeconds(7200)
        val validation = SessionValidator.validate(bedtime, waketime, SessionStatus.COMPLETED, now = now)
        assertTrue(validation is ValidationResult.Invalid)
        assertEquals("End time cannot be before start time", (validation as ValidationResult.Invalid).reason)
    }

    @Test
    fun durationExceeding36Hours_isRejectedByValidator() {
        val now = Instant.now()
        val start = now.minusSeconds(40 * 3600)
        val end = now.minusSeconds(2 * 3600) // 38h duration, not in future
        val validation = SessionValidator.validate(start, end, SessionStatus.COMPLETED, now = now)
        assertTrue(validation is ValidationResult.Invalid)
        assertTrue((validation as ValidationResult.Invalid).reason.contains("exceeds realistic limit"))
    }
}
