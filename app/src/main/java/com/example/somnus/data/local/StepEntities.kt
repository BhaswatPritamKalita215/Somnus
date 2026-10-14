package com.example.somnus.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.somnus.domain.model.StepCounterCheckpoint
import com.example.somnus.domain.model.StepRecord
import com.example.somnus.domain.model.StepSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Entity(
    tableName = "step_records",
    indices = [
        Index(value = ["localDate"]),
        Index(value = ["startTime"]),
        Index(value = ["externalRecordId"])
    ]
)
data class StepRecordEntity(
    @PrimaryKey
    val id: String,
    val startTime: Long,
    val endTime: Long,
    val localDate: String, // ISO-8601 YYYY-MM-DD
    val zoneId: String,
    val stepCount: Long,
    val source: String,
    val sourceIdentifier: String,
    val deviceModel: String?,
    val externalRecordId: String?,
    val createdAt: Long
) {
    fun toDomain(): StepRecord {
        return StepRecord(
            id = id,
            startTime = Instant.ofEpochMilli(startTime),
            endTime = Instant.ofEpochMilli(endTime),
            localDate = try {
                LocalDate.parse(localDate)
            } catch (e: Exception) {
                LocalDate.now()
            },
            zoneId = try {
                ZoneId.of(zoneId)
            } catch (e: Exception) {
                ZoneId.systemDefault()
            },
            stepCount = stepCount,
            source = try {
                StepSource.valueOf(source)
            } catch (e: Exception) {
                StepSource.PHONE_HARDWARE
            },
            sourceIdentifier = sourceIdentifier,
            deviceModel = deviceModel,
            externalRecordId = externalRecordId,
            createdAt = Instant.ofEpochMilli(createdAt)
        )
    }

    companion object {
        fun fromDomain(record: StepRecord): StepRecordEntity {
            return StepRecordEntity(
                id = record.id,
                startTime = record.startTime.toEpochMilli(),
                endTime = record.endTime.toEpochMilli(),
                localDate = record.localDate.toString(),
                zoneId = record.zoneId.id,
                stepCount = record.stepCount,
                source = record.source.name,
                sourceIdentifier = record.sourceIdentifier,
                deviceModel = record.deviceModel,
                externalRecordId = record.externalRecordId,
                createdAt = record.createdAt.toEpochMilli()
            )
        }
    }
}

@Entity(tableName = "step_checkpoints")
data class StepCounterCheckpointEntity(
    @PrimaryKey
    val id: String = DEFAULT_CHECKPOINT_ID,
    val bootTimestamp: Long,
    val lastSensorValue: Float,
    val accumulatedSteps: Long,
    val lastUpdateEpochMs: Long,
    val localDate: String
) {
    fun toDomain(): StepCounterCheckpoint {
        return StepCounterCheckpoint(
            bootTimestamp = bootTimestamp,
            lastSensorValue = lastSensorValue,
            accumulatedSteps = accumulatedSteps,
            lastUpdateEpochMs = lastUpdateEpochMs,
            localDate = localDate
        )
    }

    companion object {
        const val DEFAULT_CHECKPOINT_ID = "active_hardware_checkpoint"

        fun fromDomain(
            checkpoint: StepCounterCheckpoint,
            id: String = DEFAULT_CHECKPOINT_ID
        ): StepCounterCheckpointEntity {
            return StepCounterCheckpointEntity(
                id = id,
                bootTimestamp = checkpoint.bootTimestamp,
                lastSensorValue = checkpoint.lastSensorValue,
                accumulatedSteps = checkpoint.accumulatedSteps,
                lastUpdateEpochMs = checkpoint.lastUpdateEpochMs,
                localDate = checkpoint.localDate
            )
        }
    }
}
