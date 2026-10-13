package com.example.somnus.data.repository

import com.example.somnus.domain.model.SleepSession
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId

/**
 * Repository interface for sleep session persistence and state changes.
 */
interface SleepSessionRepository {

    /**
     * Observable stream of the currently active sleep session, or null if none is active.
     * Survives application termination because it is backed by Room persistence.
     */
    val activeSession: Flow<SleepSession?>

    /**
     * Observable stream of completed sleep sessions ordered chronologically (newest first).
     */
    val completedSessions: Flow<List<SleepSession>>

    /**
     * Observable stream of all sessions regardless of status.
     */
    val allSessions: Flow<List<SleepSession>>

    /**
     * Observable stream of a single session by its unique ID.
     */
    fun getSessionById(id: String): Flow<SleepSession?>

    /**
     * Direct one-shot retrieval of the currently active session.
     */
    suspend fun getActiveSessionOneShot(): SleepSession?

    /**
     * Direct one-shot retrieval of a session by ID.
     */
    suspend fun getSessionByIdOneShot(id: String): SleepSession?

    /**
     * Total number of stored sessions.
     */
    suspend fun getSessionCount(): Int

    /**
     * Atomically starts a new manual sleep session.
     * Rejects duplicate active sessions.
     */
    suspend fun startSession(
        id: String,
        startTime: Instant,
        zoneId: ZoneId,
        notes: String? = null
    ): Result<SleepSession>

    /**
     * Atomically ends an active sleep session.
     * Validates that end time is strictly greater than start time.
     */
    suspend fun endSession(
        id: String,
        endTime: Instant,
        notes: String? = null
    ): Result<SleepSession>

    /**
     * Updates an existing session with edited or corrected data.
     * Validates timestamps before saving.
     */
    suspend fun updateSession(session: SleepSession): Result<Unit>

    /**
     * Deletes a single session by ID.
     */
    suspend fun deleteSession(id: String): Result<Unit>

    /**
     * Permanently deletes all stored sleep sessions.
     */
    suspend fun deleteAllSessions(): Result<Unit>
}
