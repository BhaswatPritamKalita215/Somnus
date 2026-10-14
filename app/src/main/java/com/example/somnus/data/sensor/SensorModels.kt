package com.example.somnus.data.sensor

import java.time.Duration
import java.time.Instant

/**
 * Coarse movement classification derived from windowed accelerometer metrics.
 *
 * Scientific disclaimer:
 * Accelerometer data from a mobile phone reflects physical movement of the device itself.
 * Device stillness DOES NOT equate to physiological sleep, nor can accelerometer readings
 * distinguish between REM, Deep (slow-wave), or Light sleep stages.
 */
enum class MovementCategory(val label: String, val scientificNote: String) {
    STILLNESS(
        label = "Stillness",
        scientificNote = "Device is motionless. Reflects resting state or placement on a surface, not verified neurological sleep."
    ),
    MINOR_RESTLESSNESS(
        label = "Restlessness",
        scientificNote = "Low-amplitude movement detected, consistent with bed repositioning or peripheral vibrations."
    ),
    ACTIVE_MOVEMENT(
        label = "Active Movement",
        scientificNote = "Significant device acceleration detected, indicative of handling or gross physical motion."
    ),
    OFF_PERSON_STATIONARY(
        label = "Stationary on Surface",
        scientificNote = "Continuous invariant baseline indicates the phone is placed on a nightstand or flat surface."
    ),
    INTERRUPTED_UNMONITORED(
        label = "Interrupted / Unmonitored",
        scientificNote = "Sensor acquisition was paused, terminated by the operating system, or device was rebooted."
    )
}

/**
 * 60-second summarized movement observation.
 * Aggregates high-frequency sensor readings into constant-space statistical summaries
 * to eliminate battery drain and database bloat.
 */
data class MovementEpoch(
    val epochIndex: Long,
    val startTime: Instant,
    val endTime: Instant,
    val meanMagnitude: Double,
    val stdDevMagnitude: Double,
    val peakMagnitude: Double,
    val category: MovementCategory,
    val isScreenOn: Boolean,
    val isCharging: Boolean
)

/**
 * Categorization of reasons why tracking was suspended or interrupted.
 */
enum class InterruptionReason(val explanation: String) {
    BATTERY_SAVER_ENABLED("OS Battery Saver throttled or restricted background execution."),
    LOW_BATTERY_SHUTDOWN("Battery fell below safety threshold (< 15%) without an active charging connection."),
    DEVICE_REBOOT("Operating system rebooted during the tracking window."),
    SYSTEM_KILLED_SERVICE("Background process was reclaimed by OS memory manager (LMK)."),
    SCREEN_USER_INTERACTION("User illuminated screen and interacted with device."),
    SENSOR_INACTIVE("Hardware sensor failed to deliver samples within the expected timeout.")
}

/**
 * Record of an unmonitored gap or interruption in tracking.
 */
data class TrackingInterruption(
    val id: String,
    val startTime: Instant,
    val endTime: Instant?,
    val reason: InterruptionReason,
    val notes: String? = null
) {
    val duration: Duration?
        get() = if (endTime != null && !endTime.isBefore(startTime)) {
            Duration.between(startTime, endTime)
        } else {
            null
        }
}

/**
 * Hardware and power environment audit report.
 */
data class SensorAvailabilityReport(
    val hasAccelerometer: Boolean,
    val sensorName: String?,
    val sensorVendor: String?,
    val isBatterySaverActive: Boolean,
    val batteryLevelPercent: Int,
    val isCharging: Boolean,
    val limitationsNotice: String = SCIENTIFIC_LIMITATIONS_NOTICE
) {
    companion object {
        const val SCIENTIFIC_LIMITATIONS_NOTICE =
            "Phone sensor tracking observes physical phone stillness and ambient context. " +
            "It does NOT measure polysomnography, brainwaves (EEG), eye movements (EOG), " +
            "or heart rate variability (HRV). Stillness on a nightstand must not be interpreted as physiological sleep."
    }
}
