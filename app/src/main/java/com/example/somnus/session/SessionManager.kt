package com.example.somnus.session

import com.example.somnus.data.repository.SleepSessionRepository
import com.example.somnus.domain.analysis.DurationCalculator
import com.example.somnus.domain.model.SleepSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Coordinates sleep session lifecycle.
 *
 * Core principles:
 * 1. Active session state is backed by the Room database. It automatically survives process death.
 * 2. Elapsed duration is recalculated dynamically from the persisted [SleepSession.startTime]
 *    rather than storing a ticking counter.
 * 3. Enforces single active session rule.
 */
class SessionManager(
    private val repository: SleepSessionRepository,
    private val externalScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    /**
     * Observable active session state, backed by Room.
     */
    val activeSession: StateFlow<SleepSession?> = repository.activeSession
        .stateIn(
            scope = externalScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    /**
     * Recalculates elapsed duration from the persisted start instant.
     */
    fun calculateElapsed(): Duration {
        val session = activeSession.value ?: return Duration.ZERO
        return if (session.isActive) DurationCalculator.calculateElapsed(session.startTime) else Duration.ZERO
    }

    /**
     * Starts a new manual sleep tracking session.
     *
     * @param notes Optional user notes at session start.
     * @return [Result] containing the created [SleepSession] or failure if one is already active.
     */
    suspend fun startSession(notes: String? = null): Result<SleepSession> {
        val existing = repository.getActiveSessionOneShot()
        if (existing != null) {
            return Result.failure(
                IllegalStateException("A session is already active. End the existing session first.")
            )
        }

        val id = UUID.randomUUID().toString()
        val now = Instant.now()
        val zoneId = ZoneId.systemDefault()

        return repository.startSession(
            id = id,
            startTime = now,
            zoneId = zoneId,
            notes = notes
        )
    }

    /**
     * Ends the currently active session.
     *
     * @param notes Optional notes updated upon completion.
     * @return [Result] containing the completed [SleepSession] or failure if no session is active.
     */
    suspend fun endSession(notes: String? = null): Result<SleepSession> {
        val current = repository.getActiveSessionOneShot()
            ?: return Result.failure(IllegalStateException("No active session to end."))

        val now = Instant.now()
        return repository.endSession(
            id = current.id,
            endTime = now,
            notes = notes
        )
    }

    /**
     * Discards the currently active session without recording it as completed.
     */
    suspend fun discardActiveSession(): Result<Unit> {
        val current = repository.getActiveSessionOneShot()
            ?: return Result.failure(IllegalStateException("No active session to discard."))

        return repository.deleteSession(current.id)
    }
}
