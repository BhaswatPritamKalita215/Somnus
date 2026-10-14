package com.example.somnus.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Data provenance classification for recorded physical steps.
 */
enum class StepSource(val displayName: String, val description: String) {
    PHONE_HARDWARE(
        displayName = "Phone Sensor",
        description = "Steps measured directly via on-device hardware step counter sensor."
    ),
    HEALTH_CONNECT_WEARABLE(
        displayName = "Wearable (Health Connect)",
        description = "Steps imported from compatible wearable device via Android Health Connect."
    ),
    HEALTH_CONNECT_PHONE(
        displayName = "Other Phone App (Health Connect)",
        description = "Steps imported from external phone-based fitness app via Health Connect."
    ),
    MANUAL_ENTRY(
        displayName = "Manual Entry",
        description = "User-reported activity count."
    )
}

/**
 * Reason recorded when reconciling multiple step sources for a given day or interval.
 */
enum class StepReconciliationRationale(val description: String) {
    SINGLE_SOURCE(
        "Only one data source reported steps for this period."
    ),
    NON_OVERLAPPING_SUMMED(
        "Sources recorded activity during non-overlapping time windows; intervals safely combined."
    ),
    DEDUPLICATED_WEARABLE_PREFERRED(
        "Overlapping phone and wearable activity detected; continuous on-body wearable record preferred to avoid double counting."
    ),
    DEDUPLICATED_PHONE_HIGHER_COVERAGE(
        "Overlapping records detected; phone sensor recorded significant activity missing from wearable."
    ),
    HEALTH_CONNECT_NATIVE_AGGREGATION(
        "Reconciled using upstream Health Connect native aggregation."
    ),
    CONFLICT_UNCERTAIN(
        "Inconsistent overlapping records detected without clear priority; total reported with uncertainty flag."
    )
}

/**
 * Domain representation of an interval-based or continuous step observation.
 */
data class StepRecord(
    val id: String,
    val startTime: Instant,
    val endTime: Instant,
    val localDate: LocalDate,
    val zoneId: ZoneId,
    val stepCount: Long,
    val source: StepSource,
    val sourceIdentifier: String,
    val deviceModel: String? = null,
    val externalRecordId: String? = null,
    val createdAt: Instant = Instant.now()
) {
    init {
        require(stepCount >= 0) { "Step count cannot be negative: $stepCount" }
        require(!endTime.isBefore(startTime)) { "End time cannot be before start time" }
    }

    val duration: Duration
        get() = Duration.between(startTime, endTime)
}

/**
 * Daily activity summary with source attribution, goal progress, and reconciliation transparency.
 */
data class DailyStepSummary(
    val date: LocalDate,
    val totalSteps: Long,
    val goalSteps: Int,
    val phoneSteps: Long = 0L,
    val healthConnectSteps: Long = 0L,
    val rationale: StepReconciliationRationale = StepReconciliationRationale.SINGLE_SOURCE,
    val isGoalReached: Boolean = totalSteps >= goalSteps,
    val goalProgressPercentage: Float = if (goalSteps > 0) {
        ((totalSteps.toFloat() / goalSteps.toFloat()) * 100f).coerceIn(0f, 1000f)
    } else 0f,
    val hourlyDistribution: Map<Int, Long> = emptyMap(), // Hour of day (0-23) -> step count
    val sourcesPresent: Set<StepSource> = emptySet(),
    val hasUncertainty: Boolean = false,
    val rawRecordsCount: Int = 0
)

/**
 * Checkpoint for phone hardware step counter (TYPE_STEP_COUNTER).
 * Stores cumulative hardware counter baseline across reboots and process deaths.
 */
data class StepCounterCheckpoint(
    val bootTimestamp: Long,
    val lastSensorValue: Float,
    val accumulatedSteps: Long,
    val lastUpdateEpochMs: Long,
    val localDate: String
)
