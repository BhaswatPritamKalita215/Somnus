package com.example.somnus.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

/**
 * Interface abstracting low-level AndroidX Health Connect Client calls.
 * Allows decoupling Somnus business logic from direct Android platform services
 * and enables robust, deterministic unit and integration test doubles.
 */
interface HealthConnectClientWrapper {
    fun getAvailability(): HealthConnectAvailability
    suspend fun hasPermissions(): Boolean
    suspend fun readSleepRecords(startTime: Instant, endTime: Instant): List<HealthConnectSleepRecord>
    suspend fun hasStepPermissions(): Boolean
    suspend fun readStepRecords(startTime: Instant, endTime: Instant): List<HealthConnectStepRecord>
    suspend fun hasEnergyExpenditurePermissions(): Boolean
    suspend fun readEnergyExpenditure(startTime: Instant, endTime: Instant): HealthConnectEnergyExpenditure
}

/**
 * Production implementation of [HealthConnectClientWrapper] utilizing the official
 * [androidx.health.connect.client.HealthConnectClient].
 */
class DefaultHealthConnectClientWrapper(
    private val context: Context
) : HealthConnectClientWrapper {

    companion object {
        val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(SleepSessionRecord::class)
        )
        val STEP_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class)
        )
        val ENERGY_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
        )
    }

    override fun getAvailability(): HealthConnectAvailability {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UPDATE_REQUIRED
            else -> {
                // If on Android 8+ but not installed or unsupported
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    HealthConnectAvailability.NOT_INSTALLED
                } else {
                    HealthConnectAvailability.NOT_SUPPORTED
                }
            }
        }
    }

    override suspend fun hasPermissions(): Boolean {
        if (getAvailability() != HealthConnectAvailability.AVAILABLE) return false
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(REQUIRED_PERMISSIONS)
    }

    override suspend fun hasStepPermissions(): Boolean {
        if (getAvailability() != HealthConnectAvailability.AVAILABLE) return false
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(STEP_PERMISSIONS)
    }

    override suspend fun readSleepRecords(
        startTime: Instant,
        endTime: Instant
    ): List<HealthConnectSleepRecord> {
        if (getAvailability() != HealthConnectAvailability.AVAILABLE) {
            throw IllegalStateException("Health Connect is not available on this device.")
        }
        val client = HealthConnectClient.getOrCreate(context)
        val request = ReadRecordsRequest(
            recordType = SleepSessionRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
        )
        val response = client.readRecords(request)

        return response.records.map { record ->
            HealthConnectSleepRecord(
                id = record.metadata.id,
                startTime = record.startTime,
                endTime = record.endTime,
                title = record.title,
                notes = record.notes,
                sourcePackage = record.metadata.dataOrigin.packageName,
                deviceModel = record.metadata.device?.model
            )
        }
    }

    override suspend fun readStepRecords(
        startTime: Instant,
        endTime: Instant
    ): List<HealthConnectStepRecord> {
        if (getAvailability() != HealthConnectAvailability.AVAILABLE) {
            throw IllegalStateException("Health Connect is not available on this device.")
        }
        val client = HealthConnectClient.getOrCreate(context)
        val request = ReadRecordsRequest(
            recordType = StepsRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
        )
        val response = client.readRecords(request)

        return response.records.map { record ->
            HealthConnectStepRecord(
                id = record.metadata.id,
                startTime = record.startTime,
                endTime = record.endTime,
                count = record.count,
                sourcePackage = record.metadata.dataOrigin.packageName,
                deviceModel = record.metadata.device?.model
            )
        }
    }

    override suspend fun hasEnergyExpenditurePermissions(): Boolean {
        if (getAvailability() != HealthConnectAvailability.AVAILABLE) return false
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        return granted.contains(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)) ||
               granted.contains(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))
    }

    override suspend fun readEnergyExpenditure(
        startTime: Instant,
        endTime: Instant
    ): HealthConnectEnergyExpenditure {
        if (getAvailability() != HealthConnectAvailability.AVAILABLE) {
            return HealthConnectEnergyExpenditure()
        }
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()

        var activeKcal: Double? = null
        var totalKcal: Double? = null
        var sourcePackage: String? = null

        if (granted.contains(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))) {
            try {
                val activeRequest = ReadRecordsRequest(
                    recordType = ActiveCaloriesBurnedRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
                val activeResponse = client.readRecords(activeRequest)
                if (activeResponse.records.isNotEmpty()) {
                    activeKcal = activeResponse.records.sumOf { it.energy.inKilocalories }
                    sourcePackage = activeResponse.records.firstOrNull()?.metadata?.dataOrigin?.packageName
                }
            } catch (_: Exception) {}
        }

        if (granted.contains(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))) {
            try {
                val totalRequest = ReadRecordsRequest(
                    recordType = TotalCaloriesBurnedRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
                val totalResponse = client.readRecords(totalRequest)
                if (totalResponse.records.isNotEmpty()) {
                    totalKcal = totalResponse.records.sumOf { it.energy.inKilocalories }
                    if (sourcePackage == null) {
                        sourcePackage = totalResponse.records.firstOrNull()?.metadata?.dataOrigin?.packageName
                    }
                }
            } catch (_: Exception) {}
        }

        return HealthConnectEnergyExpenditure(
            activeCaloriesKcal = activeKcal,
            totalCaloriesKcal = totalKcal,
            sourcePackage = sourcePackage
        )
    }
}
