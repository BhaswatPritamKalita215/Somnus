package com.example.somnus.domain.analysis

import com.example.somnus.domain.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class SessionValidatorTest {

    private val now = Instant.parse("2026-10-14T08:00:00Z")

    @Test
    fun validate_validActiveSession_returnsValid() {
        val start = now.minus(Duration.ofHours(2))
        val result = SessionValidator.validate(
            start = start,
            end = null,
            status = SessionStatus.ACTIVE,
            now = now
        )
        assertTrue(result.isValid)
    }

    @Test
    fun validate_activeSessionWithEndTime_returnsInvalid() {
        val start = now.minus(Duration.ofHours(2))
        val end = now
        val result = SessionValidator.validate(
            start = start,
            end = end,
            status = SessionStatus.ACTIVE,
            now = now
        )
        assertFalse(result.isValid)
        assertTrue((result as ValidationResult.Invalid).reason.contains("Active sessions cannot have an end time"))
    }

    @Test
    fun validate_validCompletedSession_returnsValid() {
        val start = now.minus(Duration.ofHours(8))
        val end = now.minus(Duration.ofMinutes(15))
        val result = SessionValidator.validate(
            start = start,
            end = end,
            status = SessionStatus.COMPLETED,
            now = now
        )
        assertTrue(result.isValid)
    }

    @Test
    fun validate_completedSessionWithoutEndTime_returnsInvalid() {
        val start = now.minus(Duration.ofHours(8))
        val result = SessionValidator.validate(
            start = start,
            end = null,
            status = SessionStatus.COMPLETED,
            now = now
        )
        assertFalse(result.isValid)
        assertTrue((result as ValidationResult.Invalid).reason.contains("must have an end time"))
    }

    @Test
    fun validate_endBeforeStart_returnsInvalid() {
        val start = now.minus(Duration.ofHours(4))
        val end = now.minus(Duration.ofHours(6))
        val result = SessionValidator.validate(
            start = start,
            end = end,
            status = SessionStatus.COMPLETED,
            now = now
        )
        assertFalse(result.isValid)
        assertTrue((result as ValidationResult.Invalid).reason.contains("cannot be before start time"))
    }

    @Test
    fun validate_zeroLengthCompletedSession_returnsInvalid() {
        val instant = now.minus(Duration.ofHours(4))
        val result = SessionValidator.validate(
            start = instant,
            end = instant,
            status = SessionStatus.COMPLETED,
            now = now
        )
        assertFalse(result.isValid)
        assertTrue((result as ValidationResult.Invalid).reason.contains("cannot be zero"))
    }

    @Test
    fun validate_startInFuture_returnsInvalid() {
        val futureStart = now.plus(Duration.ofHours(1))
        val result = SessionValidator.validate(
            start = futureStart,
            end = null,
            status = SessionStatus.ACTIVE,
            now = now
        )
        assertFalse(result.isValid)
        assertTrue((result as ValidationResult.Invalid).reason.contains("cannot be in the future"))
    }

    @Test
    fun validate_unrealisticDuration_returnsInvalid() {
        val start = now.minus(Duration.ofHours(40))
        val end = now
        val result = SessionValidator.validate(
            start = start,
            end = end,
            status = SessionStatus.COMPLETED,
            now = now
        )
        assertFalse(result.isValid)
        assertTrue((result as ValidationResult.Invalid).reason.contains("exceeds realistic limit"))
    }
}
