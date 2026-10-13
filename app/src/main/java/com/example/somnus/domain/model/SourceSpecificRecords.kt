package com.example.somnus.domain.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Explicit domain record representing manual time-in-bed tracking.
 *
 * Scientific note: Manually entered or tracked start and end times reflect
 * "Time in Bed" (TIB), NOT actual physiological sleep duration or sleep architecture.
 */
data class ManualBedtimeRecord(
    val session: SleepSession
) : SleepSessionRecord by session {
    init {
        require(session.source == SessionSource.MANUAL) {
            "ManualBedtimeRecord source must be MANUAL, found: ${session.source}"
        }
    }

    /**
     * Explicitly labeled as time in bed to avoid misleading the user with false precision.
     */
    val timeInBed: Duration?
        get() = session.duration
}

/**
 * Domain specification for future phone-sensor derived estimates.
 * Established now to safeguard architectural separation and scientific transparency.
 */
interface PhoneEstimateRecord : SleepSessionRecord {
    /**
     * Confidence interval or uncertainty window in minutes (e.g. ±15 minutes).
     */
    val uncertaintyMinutes: Int

    /**
     * Sensor features contributing to the estimation (e.g. ambient light, device stillness).
     */
    val sensorSignalsUsed: Set<String>
}

/**
 * Domain specification for future wearable-imported data.
 * Established now to enforce strict validation against fabricated health metrics.
 */
interface WearableImportRecord : SleepSessionRecord {
    /**
     * External hardware or platform identifier (e.g. Health Connect, Garmin, Polar).
     */
    val deviceModel: String
    val externalRecordId: String
}
