package com.example.somnus.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Classification of how the Sleep Onset Latency (SOL) measurement was acquired.
 */
enum class SolMeasurementMethod {
    /**
     * Entered or estimated directly by the user via questionnaire, morning log, or edit.
     */
    SELF_REPORTED,

    /**
     * Derived from software heuristics or on-device sensor estimation.
     * Note: Phone motion alone does not identify physiological sleep onset with clinical accuracy.
     */
    ALGORITHM_ESTIMATE,

    /**
     * Imported from an external hardware device (e.g. EEG headband, validated tracker, polysomnography).
     */
    WEARABLE_IMPORT
}

/**
 * Flags documenting data-quality indicators, uncertainty, or editorial modifications.
 */
enum class SolQualityFlag {
    /** The user noted uncertain timing or guessed their sleep latency. */
    UNCERTAIN_TIMING,

    /** The record was altered or manually corrected after initial creation. */
    EDITED_MANUALLY,

    /** Latency is unusually long (> 120 minutes), indicating severe insomnia or recording anomaly. */
    OUTLIER_EXCESSIVE_LATENCY,

    /** No specific attempt-to-sleep timestamp was logged; latency duration was entered as an estimated number of minutes. */
    MISSING_SLEEP_ATTEMPT
}

/**
 * Persistent domain model for a nightly Sleep Onset Latency (SOL) record.
 *
 * Distinct sourced observations are preserved so that wearable or algorithmic estimates
 * never overwrite a self-reported value.
 */
data class SolRecord(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String? = null,
    val reportingDate: LocalDate,
    val zoneId: ZoneId = ZoneId.systemDefault(),
    val latencyMinutes: Int,
    val method: SolMeasurementMethod = SolMeasurementMethod.SELF_REPORTED,
    val sourceIdentifier: String = "manual",
    val sourceDeviceMetadata: String? = null,
    val qualityFlags: Set<SolQualityFlag> = emptySet(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val attemptToSleepTime: Instant? = null,
    val sleepOnsetTime: Instant? = null,
    val notes: String? = null
)
