package com.example.somnus.domain.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Lifecycle status of a sleep tracking session.
 */
enum class SessionStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED
}

/**
 * Data-source classification for sleep records.
 *
 * In Milestone 1, only [MANUAL] records are created. Domain types for [PHONE_ESTIMATE]
 * and [WEARABLE_IMPORT] are defined here to establish source-aware typing and prevent
 * future automated estimates from being falsely conflated with manual time-in-bed records.
 */
enum class SessionSource(val displayName: String, val description: String) {
    MANUAL(
        displayName = "Manual Time in Bed",
        description = "User-reported bedtime and wake time. Represents time spent in bed, not verified physiological sleep stages."
    ),
    PHONE_ESTIMATE(
        displayName = "Phone Sensor Estimate",
        description = "Estimated sleep window derived from device sensors (stationary state, screen usage). Uncertainty bounds apply."
    ),
    WEARABLE_IMPORT(
        displayName = "Imported Wearable Data",
        description = "Physiological sleep measurements imported from compatible wearable hardware."
    )
}

/**
 * Domain interface for sleep records.
 * Differentiates manual time-in-bed logs from sensor-derived sleep estimates
 * and wearable imports without treating them as scientifically equivalent.
 */
interface SleepSessionRecord {
    val id: String
    val startTime: Instant
    val endTime: Instant?
    val zoneId: ZoneId
    val status: SessionStatus
    val source: SessionSource
    val createdAt: Instant
    val updatedAt: Instant
    val notes: String?
    val isCorrected: Boolean

    /**
     * Total elapsed duration between start and end if completed, or null if active.
     */
    val duration: Duration?
        get() = if (endTime != null && !endTime!!.isBefore(startTime)) {
            Duration.between(startTime, endTime)
        } else {
            null
        }
}

/**
 * Concrete domain model for a sleep tracking session.
 */
data class SleepSession(
    override val id: String,
    override val startTime: Instant,
    override val endTime: Instant? = null,
    override val zoneId: ZoneId = ZoneId.systemDefault(),
    override val status: SessionStatus = SessionStatus.ACTIVE,
    override val source: SessionSource = SessionSource.MANUAL,
    override val createdAt: Instant = Instant.now(),
    override val updatedAt: Instant = Instant.now(),
    override val notes: String? = null,
    override val isCorrected: Boolean = false
) : SleepSessionRecord {

    val isActive: Boolean
        get() = status == SessionStatus.ACTIVE && endTime == null

    val isCompleted: Boolean
        get() = status == SessionStatus.COMPLETED && endTime != null
}
