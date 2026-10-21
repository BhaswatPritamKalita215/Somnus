package com.example.somnus.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.somnus.domain.model.SleepCheckIn
import java.time.Instant

/**
 * Room entity representing an original morning check-in linked to a sleep session.
 */
@Entity(
    tableName = "sleep_check_ins",
    foreignKeys = [
        ForeignKey(
            entity = SleepSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"], unique = true),
        Index(value = ["createdAt"])
    ]
)
data class SleepCheckInEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val moodScore: Int?,
    val moodLabel: String?,
    val restedScore: Int?,
    val notes: String?,
    val createdAt: Long,
    val isSelfReported: Boolean
) {
    fun toDomain(): SleepCheckIn {
        return SleepCheckIn(
            id = id,
            sessionId = sessionId,
            moodScore = moodScore,
            moodLabel = moodLabel,
            restedScore = restedScore,
            notes = notes,
            createdAt = Instant.ofEpochMilli(createdAt),
            isSelfReported = isSelfReported
        )
    }

    companion object {
        fun fromDomain(domain: SleepCheckIn): SleepCheckInEntity {
            return SleepCheckInEntity(
                id = domain.id,
                sessionId = domain.sessionId,
                moodScore = domain.moodScore,
                moodLabel = domain.moodLabel,
                restedScore = domain.restedScore,
                notes = domain.notes,
                createdAt = domain.createdAt.toEpochMilli(),
                isSelfReported = domain.isSelfReported
            )
        }
    }
}
