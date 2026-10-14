package com.example.somnus.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.somnus.domain.model.SolMeasurementMethod
import com.example.somnus.domain.model.SolQualityFlag
import com.example.somnus.domain.model.SolRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Room entity representing an individual Sleep Onset Latency record.
 */
@Entity(
    tableName = "sol_records",
    foreignKeys = [
        ForeignKey(
            entity = SleepSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["reportingDate"]),
        Index(value = ["sessionId"])
    ]
)
data class SolRecordEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String?,
    val reportingDate: String, // ISO-8601 YYYY-MM-DD
    val zoneId: String,
    val latencyMinutes: Int,
    val method: String,
    val sourceIdentifier: String,
    val sourceDeviceMetadata: String?,
    val qualityFlags: String, // Comma-separated flag names
    val createdAt: Long,
    val updatedAt: Long,
    val attemptToSleepTime: Long?,
    val sleepOnsetTime: Long?,
    val notes: String?
) {
    fun toDomain(): SolRecord {
        val parsedFlags = if (qualityFlags.isBlank()) {
            emptySet()
        } else {
            qualityFlags.split(",")
                .filter { it.isNotBlank() }
                .mapNotNull { flagStr ->
                    try {
                        SolQualityFlag.valueOf(flagStr.trim())
                    } catch (e: Exception) {
                        null
                    }
                }
                .toSet()
        }

        return SolRecord(
            id = id,
            sessionId = sessionId,
            reportingDate = try {
                LocalDate.parse(reportingDate)
            } catch (e: Exception) {
                LocalDate.now()
            },
            zoneId = try {
                ZoneId.of(zoneId)
            } catch (e: Exception) {
                ZoneId.systemDefault()
            },
            latencyMinutes = latencyMinutes,
            method = try {
                SolMeasurementMethod.valueOf(method)
            } catch (e: Exception) {
                SolMeasurementMethod.SELF_REPORTED
            },
            sourceIdentifier = sourceIdentifier,
            sourceDeviceMetadata = sourceDeviceMetadata,
            qualityFlags = parsedFlags,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
            attemptToSleepTime = attemptToSleepTime?.let { Instant.ofEpochMilli(it) },
            sleepOnsetTime = sleepOnsetTime?.let { Instant.ofEpochMilli(it) },
            notes = notes
        )
    }

    companion object {
        fun fromDomain(record: SolRecord): SolRecordEntity {
            return SolRecordEntity(
                id = record.id,
                sessionId = record.sessionId,
                reportingDate = record.reportingDate.toString(),
                zoneId = record.zoneId.id,
                latencyMinutes = record.latencyMinutes,
                method = record.method.name,
                sourceIdentifier = record.sourceIdentifier,
                sourceDeviceMetadata = record.sourceDeviceMetadata,
                qualityFlags = record.qualityFlags.joinToString(",") { it.name },
                createdAt = record.createdAt.toEpochMilli(),
                updatedAt = record.updatedAt.toEpochMilli(),
                attemptToSleepTime = record.attemptToSleepTime?.toEpochMilli(),
                sleepOnsetTime = record.sleepOnsetTime?.toEpochMilli(),
                notes = record.notes
            )
        }
    }
}
