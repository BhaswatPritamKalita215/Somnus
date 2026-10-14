package com.example.somnus.data.healthconnect

import com.example.somnus.data.repository.SleepSessionRepository
import com.example.somnus.domain.model.SessionSource
import com.example.somnus.domain.model.SessionStatus
import com.example.somnus.domain.model.SleepSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * High-level manager coordinating synchronization and deduplication of sleep records
 * from Android Health Connect into Somnus local storage.
 *
 * Guarantees:
 * 1. Scientific transparency: Imported records are explicitly attributed with source [SessionSource.WEARABLE_IMPORT]
 *    and never silently conflated with self-reported manual time-in-bed records.
 * 2. Non-destructive conflict handling: Existing manual records are NEVER overwritten by imported records.
 * 3. Exact deduplication: Identical time intervals from the same or external sources are safely ignored.
 */
class HealthConnectManager(
    private val clientWrapper: HealthConnectClientWrapper,
    private val repository: SleepSessionRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    fun checkAvailability(): HealthConnectAvailability {
        return clientWrapper.getAvailability()
    }

    suspend fun hasPermissions(): Boolean = withContext(ioDispatcher) {
        clientWrapper.hasPermissions()
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
}
