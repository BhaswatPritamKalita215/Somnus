package com.example.somnus.data.repository

import com.example.somnus.data.local.SleepSessionDao
import com.example.somnus.data.local.SleepSessionEntity
import com.example.somnus.domain.analysis.SessionValidator
import com.example.somnus.domain.analysis.ValidationResult
import com.example.somnus.domain.model.SessionSource
import com.example.somnus.domain.model.SessionStatus
import com.example.somnus.domain.model.SleepSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

class SleepSessionRepositoryImpl(
    private val dao: SleepSessionDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SleepSessionRepository {

    override val activeSession: Flow<SleepSession?> =
        dao.observeActiveSession().map { it?.toDomain() }

    override val completedSessions: Flow<List<SleepSession>> =
        dao.observeCompletedSessions().map { list -> list.map { it.toDomain() } }

    override val allSessions: Flow<List<SleepSession>> =
        dao.observeAllSessions().map { list -> list.map { it.toDomain() } }

    override fun getSessionById(id: String): Flow<SleepSession?> =
        dao.observeSessionById(id).map { it?.toDomain() }

    override suspend fun getActiveSessionOneShot(): SleepSession? = withContext(ioDispatcher) {
        dao.getActiveSession()?.toDomain()
    }

    override suspend fun getSessionByIdOneShot(id: String): SleepSession? = withContext(ioDispatcher) {
        dao.getSessionById(id)?.toDomain()
    }

    override suspend fun getSessionCount(): Int = withContext(ioDispatcher) {
        dao.getSessionCount()
    }

    override suspend fun startSession(
        id: String,
        startTime: Instant,
        zoneId: ZoneId,
        notes: String?
    ): Result<SleepSession> = withContext(ioDispatcher) {
        val validation = SessionValidator.validate(
            start = startTime,
            end = null,
            status = SessionStatus.ACTIVE
        )
        if (validation is ValidationResult.Invalid) {
            return@withContext Result.failure(IllegalArgumentException(validation.reason))
        }

        val session = SleepSession(
            id = id,
            startTime = startTime,
            endTime = null,
            zoneId = zoneId,
            status = SessionStatus.ACTIVE,
            source = SessionSource.MANUAL,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            notes = notes,
            isCorrected = false
        )

        try {
            dao.startNewSession(SleepSessionEntity.fromDomain(session))
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun endSession(
        id: String,
        endTime: Instant,
        notes: String?
    ): Result<SleepSession> = withContext(ioDispatcher) {
        val active = dao.getSessionById(id)
            ?: return@withContext Result.failure(IllegalStateException("Session $id does not exist"))

        val validation = SessionValidator.validate(
            start = Instant.ofEpochMilli(active.startTime),
            end = endTime,
            status = SessionStatus.COMPLETED
        )
        if (validation is ValidationResult.Invalid) {
            return@withContext Result.failure(IllegalArgumentException(validation.reason))
        }

        try {
            val completedEntity = dao.completeSession(
                id = id,
                endTimeEpochMs = endTime.toEpochMilli(),
                updatedAtEpochMs = Instant.now().toEpochMilli(),
                notes = notes
            )
            Result.success(completedEntity.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateSession(session: SleepSession): Result<Unit> = withContext(ioDispatcher) {
        val validation = SessionValidator.validate(
            start = session.startTime,
            end = session.endTime,
            status = session.status
        )
        if (validation is ValidationResult.Invalid) {
            return@withContext Result.failure(IllegalArgumentException(validation.reason))
        }

        try {
            val updated = session.copy(
                isCorrected = true,
                updatedAt = Instant.now()
            )
            dao.update(SleepSessionEntity.fromDomain(updated))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteSession(id: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val deletedRows = dao.deleteById(id)
            if (deletedRows > 0) {
                Result.success(Unit)
            } else {
                Result.failure(NoSuchElementException("No session found with ID $id"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAllSessions(): Result<Unit> = withContext(ioDispatcher) {
        try {
            dao.deleteAll()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
