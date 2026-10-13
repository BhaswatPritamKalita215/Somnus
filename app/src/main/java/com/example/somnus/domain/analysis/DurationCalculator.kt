package com.example.somnus.domain.analysis

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Robust duration calculations and formatting utilities for sleep tracking.
 *
 * Implements exact Instant arithmetic:
 * - Immune to timezone shifts and DST discontinuities because [Instant] represents unambiguous UTC epoch time.
 * - Handles sessions crossing midnight seamlessly.
 * - Persists and works with absolute timestamps rather than ticking counters.
 */
object DurationCalculator {

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

    /**
     * Calculates the exact duration between start and end instants.
     *
     * @throws IllegalArgumentException if [end] is before [start] or if duration is negative/zero when required.
     */
    fun calculateDuration(start: Instant, end: Instant): Duration {
        require(!end.isBefore(start)) {
            "End time ($end) cannot be before start time ($start)"
        }
        val duration = Duration.between(start, end)
        require(!duration.isZero) {
            "Session duration must be greater than zero"
        }
        return duration
    }

    /**
     * Calculates the currently elapsed duration from [start] to [now].
     * Clamps negative durations (e.g. due to minor system clock adjustments) to zero.
     */
    fun calculateElapsed(start: Instant, now: Instant = Instant.now()): Duration {
        val duration = Duration.between(start, now)
        return if (duration.isNegative) Duration.ZERO else duration
    }

    /**
     * Formats a duration in a human-readable format like "7h 45m" or "45m".
     */
    fun formatDuration(duration: Duration?): String {
        if (duration == null || duration.isZero) return "0m"
        val totalMinutes = duration.toMinutes()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    /**
     * Formats an elapsed duration for a live active tracking ticker: "HH:mm:ss".
     */
    fun formatElapsed(duration: Duration): String {
        val seconds = duration.seconds
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, remainingSeconds)
    }

    /**
     * Formats the wall-clock time in the given timezone (e.g. "11:30 PM").
     */
    fun formatTime(instant: Instant, zoneId: ZoneId): String {
        return instant.atZone(zoneId).format(timeFormatter)
    }

    /**
     * Formats the local date in the given timezone (e.g. "Mon, Oct 12").
     */
    fun formatDate(instant: Instant, zoneId: ZoneId): String {
        return instant.atZone(zoneId).format(dateFormatter)
    }

    /**
     * Formats a complete session interval display (e.g. "11:30 PM - 7:15 AM").
     */
    fun formatInterval(start: Instant, end: Instant?, zoneId: ZoneId): String {
        val startFormatted = formatTime(start, zoneId)
        if (end == null) return "Started $startFormatted"
        val endFormatted = formatTime(end, zoneId)
        return "$startFormatted – $endFormatted"
    }
}
