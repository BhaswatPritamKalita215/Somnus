package com.example.somnus.data.healthconnect

import com.example.somnus.data.repository.SleepSessionRepository
import com.example.somnus.data.repository.StepRepository
import com.example.somnus.domain.model.SessionSource
import com.example.somnus.domain.model.SessionStatus
import com.example.somnus.domain.model.SleepSession
import com.example.somnus.domain.model.StepRecord
import com.example.somnus.domain.model.StepSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * High-level manager coordinating synchronization and deduplication of sleep records
 * and step records from Android Health Connect into Somnus local storage.
 *
 * Guarantees:
 * 1. Scientific transparency: Imported records are explicitly attributed with source [SessionSource.WEARABLE_IMPORT]
 *    or [StepSource.HEALTH_CONNECT_WEARABLE] and never silently conflated with self-reported manual records.
 * 2. Non-destructive conflict handling: Existing manual records are NEVER overwritten by imported records.
 * 3. Exact deduplication: Identical records by external ID or exact time intervals are safely ignored.
 */
class HealthConnectManager(
    private val clientWrapper: HealthConnectClientWrapper,
    private val repository: SleepSessionRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val stepRepository: StepRepository? = null
) {

    fun checkAvailability(): HealthConnectAvailability {
        return clientWrapper.getAvailability()
    }

    suspend fun hasPermissions(): Boolean = withContext(ioDispatcher) {
        clientWrapper.hasPermissions()
    }

    suspend fun hasStepPermissions(): Boolean = withContext(ioDispatcher) {
        clientWrapper.hasStepPermissions()
    }

    suspend fun syncSleepRecords(
        startTime: Instant,
        endTime: Instant
    ): Result<HealthConnectSyncSummary> = withContext(ioDispatcher) {
        val availability = clientWrapper.getAvailability()
        if (availability != HealthConnectAvailability.AVAILABLE) {
            return@withContext Result.failure(
                IllegalStateException("Health Connect is unavailable: ${availability.description}")
            )
        }

        if (!clientWrapper.hasPermissions()) {
            return@withContext Result.failure(
                SecurityException("Health Connect sleep read permission is not granted.")
            )
        }

        val records = try {
            clientWrapper.readSleepRecords(startTime, endTime)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        if (records.isEmpty()) {
            return@withContext Result.success(
                HealthConnectSyncSummary(
                    importedCount = 0,
                    duplicateCount = 0,
                    conflictCount = 0,
                    totalQueried = 0,
                    message = "No sleep records found in Health Connect for the selected interval."
                )
            )
        }

        var imported = 0
        var duplicates = 0
        var conflicts = 0

        for (record in records) {
            // Find any overlapping sessions in the database
            val overlapping = repository.findOverlappingSessions(record.startTime, record.endTime)

            // Exact match deduplication
            val exactMatch = overlapping.firstOrNull { existing ->
                existing.startTime == record.startTime && existing.endTime == record.endTime
            }

            if (exactMatch != null) {
                duplicates++
                continue
            }

            // If there's an overlap that isn't an exact match, flag conflict
            if (overlapping.isNotEmpty()) {
                conflicts++
                continue
            }

            // Create clean wearable import session
            val noteParts = mutableListOf<String>()
            noteParts.add("[Imported from Health Connect]")
            record.deviceModel?.let { noteParts.add("Device: $it") }
            record.sourcePackage?.let { noteParts.add("App: $it") }
            record.title?.let { noteParts.add("Title: $it") }
            record.notes?.let { noteParts.add("Note: $it") }

            val sessionId = "hc_" + (record.id.ifBlank { UUID.randomUUID().toString() })
            val session = SleepSession(
                id = sessionId,
                startTime = record.startTime,
                endTime = record.endTime,
                zoneId = ZoneId.systemDefault(),
                status = SessionStatus.COMPLETED,
                source = SessionSource.WEARABLE_IMPORT,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                notes = noteParts.joinToString(" | "),
                isCorrected = false
            )

            val insertResult = repository.insertCompletedSession(session)
            if (insertResult.isSuccess) {
                imported++
            } else {
                conflicts++
            }
        }

        val summaryMessage = "Sync complete. Imported: $imported, Duplicate: $duplicates, Conflict: $conflicts."
        Result.success(
            HealthConnectSyncSummary(
                importedCount = imported,
                duplicateCount = duplicates,
                conflictCount = conflicts,
                totalQueried = records.size,
                message = summaryMessage
            )
        )
    }

    suspend fun syncStepRecords(
        startTime: Instant,
        endTime: Instant
    ): Result<HealthConnectSyncSummary> = withContext(ioDispatcher) {
        val availability = clientWrapper.getAvailability()
        if (availability != HealthConnectAvailability.AVAILABLE) {
            return@withContext Result.failure(
                IllegalStateException("Health Connect is unavailable: ${availability.description}")
            )
        }

        if (!clientWrapper.hasStepPermissions()) {
            return@withContext Result.failure(
                SecurityException("Health Connect step read permission is not granted.")
            )
        }

        val stepRepo = stepRepository ?: return@withContext Result.failure(
            IllegalStateException("StepRepository is not configured.")
        )

        val records = try {
            clientWrapper.readStepRecords(startTime, endTime)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        if (records.isEmpty()) {
            return@withContext Result.success(
                HealthConnectSyncSummary(
                    importedCount = 0,
                    duplicateCount = 0,
                    conflictCount = 0,
                    totalQueried = 0,
                    message = "No step records found in Health Connect for the selected interval."
                )
            )
        }

        var imported = 0
        var duplicates = 0

        for (record in records) {
            val localDate = record.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
            val existingRecords = stepRepo.getStepRecordsForDateSync(localDate)
            val alreadyExists = existingRecords.any { it.externalRecordId == record.id }

            if (alreadyExists) {
                duplicates++
                continue
            }

            val isWearable = record.deviceModel != null ||
                    record.sourcePackage?.contains("wear", ignoreCase = true) == true ||
                    record.sourcePackage?.contains("watch", ignoreCase = true) == true ||
                    record.sourcePackage?.contains("garmin", ignoreCase = true) == true ||
                    record.sourcePackage?.contains("fitbit", ignoreCase = true) == true ||
                    record.sourcePackage?.contains("polar", ignoreCase = true) == true ||
                    record.sourcePackage?.contains("oura", ignoreCase = true) == true ||
                    record.sourcePackage?.contains("whoop", ignoreCase = true) == true

            val source = if (isWearable) {
                StepSource.HEALTH_CONNECT_WEARABLE
            } else {
                StepSource.HEALTH_CONNECT_PHONE
            }

            val stepRecord = StepRecord(
                id = "hc_step_" + record.id,
                startTime = record.startTime,
                endTime = record.endTime,
                localDate = localDate,
                zoneId = ZoneId.systemDefault(),
                stepCount = record.count,
                source = source,
                sourceIdentifier = record.sourcePackage ?: "health_connect",
                deviceModel = record.deviceModel,
                externalRecordId = record.id,
                createdAt = Instant.now()
            )

            stepRepo.insertRecord(stepRecord)
            imported++
        }

        val summaryMessage = "Step sync complete. Imported: $imported, Duplicate: $duplicates."
        Result.success(
            HealthConnectSyncSummary(
                importedCount = imported,
                duplicateCount = duplicates,
                conflictCount = 0,
                totalQueried = records.size,
                message = summaryMessage
            )
        )
    }
}
