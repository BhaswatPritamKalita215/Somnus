package com.example.somnus.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepSessionDao {

    @Query("SELECT * FROM sleep_sessions WHERE status = 'ACTIVE' LIMIT 1")
    fun observeActiveSession(): Flow<SleepSessionEntity?>

    @Query("SELECT * FROM sleep_sessions WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveSession(): SleepSessionEntity?

    @Query("SELECT * FROM sleep_sessions WHERE status = 'COMPLETED' ORDER BY startTime DESC")
    fun observeCompletedSessions(): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_sessions ORDER BY startTime DESC")
    fun observeAllSessions(): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_sessions WHERE id = :id LIMIT 1")
    fun observeSessionById(id: String): Flow<SleepSessionEntity?>

    @Query("SELECT * FROM sleep_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: String): SleepSessionEntity?

    @Query("SELECT COUNT(*) FROM sleep_sessions")
    suspend fun getSessionCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: SleepSessionEntity)

    @Update
    suspend fun update(session: SleepSessionEntity)

    @Delete
    suspend fun delete(session: SleepSessionEntity)

    @Query("DELETE FROM sleep_sessions WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM sleep_sessions")
    suspend fun deleteAll()

    @Query("""
        SELECT * FROM sleep_sessions 
        WHERE status = 'COMPLETED' 
        AND (:excludeId IS NULL OR id != :excludeId)
        AND startTime < :endTimeEpochMs 
        AND endTime > :startTimeEpochMs
    """)
    suspend fun findOverlappingSessions(
        startTimeEpochMs: Long,
        endTimeEpochMs: Long,
        excludeId: String? = null
    ): List<SleepSessionEntity>

    /**
     * Atomically starts a new session in a database transaction.
     * Enforces the invariant: at most one active session can exist.
     *
     * @throws IllegalStateException if an active session already exists.
     */
    @Transaction
    suspend fun startNewSession(newSession: SleepSessionEntity) {
        val existingActive = getActiveSession()
        if (existingActive != null) {
            throw IllegalStateException(
                "Cannot start session: An active session (${existingActive.id}) is already in progress."
            )
        }
        insert(newSession)
    }

    /**
     * Atomically completes an active session in a database transaction.
     * Validates that end time is strictly greater than start time and that the session is active.
     *
     * @throws IllegalStateException if the session is not found or not currently ACTIVE.
     * @throws IllegalArgumentException if the end time is invalid or zero-length.
     */
    @Transaction
    suspend fun completeSession(
        id: String,
        endTimeEpochMs: Long,
        updatedAtEpochMs: Long,
        notes: String? = null,
        isCorrected: Boolean = false
    ): SleepSessionEntity {
        val existing = getSessionById(id)
            ?: throw IllegalStateException("Session $id not found")

        if (existing.status != "ACTIVE") {
            throw IllegalStateException("Cannot complete session $id: status is ${existing.status}")
        }

        if (endTimeEpochMs <= existing.startTime) {
            throw IllegalArgumentException(
                "End time ($endTimeEpochMs) must be strictly after start time (${existing.startTime})"
            )
        }

        val completed = existing.copy(
            endTime = endTimeEpochMs,
            status = "COMPLETED",
            updatedAt = updatedAtEpochMs,
            notes = notes ?: existing.notes,
            isCorrected = isCorrected || existing.isCorrected
        )
        update(completed)
        return completed
    }
}
