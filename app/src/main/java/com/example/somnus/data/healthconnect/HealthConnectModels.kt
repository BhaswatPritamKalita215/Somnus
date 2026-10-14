package com.example.somnus.data.healthconnect

import java.time.Instant

/**
 * Represents the device-level availability of Android Health Connect.
 */
enum class HealthConnectAvailability(val description: String) {
    AVAILABLE("Health Connect is supported and ready on this device."),
    NOT_INSTALLED("Health Connect is supported but needs to be installed from Google Play."),
    UPDATE_REQUIRED("Health Connect is installed but requires a software update."),
    NOT_SUPPORTED("Health Connect is not supported on this Android OS version or device.")
}

/**
 * Intermediate data structure holding sleep records read from Health Connect.
 */
data class HealthConnectSleepRecord(
    val id: String,
    val startTime: Instant,
    val endTime: Instant,
    val title: String? = null,
    val notes: String? = null,
    val sourcePackage: String? = null,
    val deviceModel: String? = null
)

/**
 * Intermediate data structure holding step records read from Health Connect.
 */
data class HealthConnectStepRecord(
    val id: String,
    val startTime: Instant,
    val endTime: Instant,
    val count: Long,
    val sourcePackage: String? = null,
    val deviceModel: String? = null
)

/**
 * Result summary of a Health Connect synchronization operation.
 */
data class HealthConnectSyncSummary(
    val importedCount: Int,
    val duplicateCount: Int,
    val conflictCount: Int,
    val totalQueried: Int,
    val message: String
)

/**
 * Energy expenditure metrics from Health Connect.
 * Strictly segregates active energy burned from total energy expenditure.
 */
data class HealthConnectEnergyExpenditure(
    val activeCaloriesKcal: Double? = null,
    val totalCaloriesKcal: Double? = null,
    val sourcePackage: String? = null
)

