package com.example.somnus.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.somnus.domain.model.SessionSource
import com.example.somnus.domain.model.SessionStatus
import com.example.somnus.domain.model.SleepSession
import java.time.Instant
import java.time.ZoneId

/**
 * Room database entity representing a persistent sleep session.
 *
 * Timestamps are stored as epoch milliseconds (unambiguous UTC instants).
 * Timezone information is preserved separately to ensure local wall-clock
 * representation accurately reflects the recording location, including across DST.
 */
@Entity(
    tableName = "sleep_sessions",
    indices = [
        Index(value = ["status"]),
        Index(value = ["startTime"])
    ]
)
data class SleepSessionEntity(
    @PrimaryKey
    val id: String,
    val startTime: Long,
    val endTime: Long?,
    val timezoneId: String,
    val status: String,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    val notes: String?,
    val isCorrected: Boolean
) {
    fun toDomain(): SleepSession {
        return SleepSession(
            id = id,
            startTime = Instant.ofEpochMilli(startTime),
            endTime = endTime?.let { Instant.ofEpochMilli(it) },
            zoneId = try {
                ZoneId.of(timezoneId)
            } catch (e: Exception) {
                ZoneId.systemDefault()
            },
            status = try {
                SessionStatus.valueOf(status)
            } catch (e: Exception) {
                SessionStatus.ACTIVE
            },
            source = try {
                SessionSource.valueOf(source)
            } catch (e: Exception) {
                SessionSource.MANUAL
            },
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
            notes = notes,
            isCorrected = isCorrected
        )
    }

    companion object {
        fun fromDomain(session: SleepSession): SleepSessionEntity {
            return SleepSessionEntity(
                id = session.id,
                startTime = session.startTime.toEpochMilli(),
                endTime = session.endTime?.toEpochMilli(),
                timezoneId = session.zoneId.id,
                status = session.status.name,
                source = session.source.name,
                createdAt = session.createdAt.toEpochMilli(),
                updatedAt = session.updatedAt.toEpochMilli(),
                notes = session.notes,
                isCorrected = session.isCorrected
            )
        }
    }
}
