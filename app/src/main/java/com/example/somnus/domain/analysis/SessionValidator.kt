package com.example.somnus.domain.analysis

import com.example.somnus.domain.model.SessionStatus
import java.time.Duration
import java.time.Instant

/**
 * Result of validating sleep session parameters.
 */
sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()

    val isValid: Boolean get() = this is Valid
}

/**
 * Domain rules and integrity checks for sleep sessions.
 */
object SessionValidator {

    private val MAX_SESSION_DURATION: Duration = Duration.ofHours(36)
    private val CLOCK_SKEW_ALLOWANCE: Duration = Duration.ofMinutes(5)

    /**
     * Validates proposed session timestamps.
     *
     * @param start The session start instant.
     * @param end The optional session end instant.
     * @param status The target session status.
     * @param now The reference instant for "current time" checks.
     */
    fun validate(
        start: Instant,
        end: Instant?,
        status: SessionStatus,
        now: Instant = Instant.now()
    ): ValidationResult {
        // Start time cannot be in the future (allowing slight clock drift)
        if (start.isAfter(now.plus(CLOCK_SKEW_ALLOWANCE))) {
            return ValidationResult.Invalid("Start time cannot be in the future")
        }

        if (status == SessionStatus.COMPLETED) {
            if (end == null) {
                return ValidationResult.Invalid("Completed sessions must have an end time")
            }

            if (end.isBefore(start)) {
                return ValidationResult.Invalid("End time cannot be before start time")
            }

            if (end == start) {
                return ValidationResult.Invalid("Session duration cannot be zero")
            }

            if (end.isAfter(now.plus(CLOCK_SKEW_ALLOWANCE))) {
                return ValidationResult.Invalid("End time cannot be in the future")
            }

            val duration = Duration.between(start, end)
            if (duration > MAX_SESSION_DURATION) {
                return ValidationResult.Invalid("Session duration exceeds realistic limit (max 36 hours)")
            }
        } else if (status == SessionStatus.ACTIVE) {
            if (end != null) {
                return ValidationResult.Invalid("Active sessions cannot have an end time")
            }
        }

        return ValidationResult.Valid
    }
}
