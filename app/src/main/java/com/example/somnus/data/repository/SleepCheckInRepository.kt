package com.example.somnus.data.repository

import com.example.somnus.data.local.SleepCheckInDao
import com.example.somnus.data.local.SleepCheckInEntity
import com.example.somnus.domain.model.SleepCheckIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface SleepCheckInRepository {
    fun getCheckInForSession(sessionId: String): Flow<SleepCheckIn?>
    suspend fun getCheckInForSessionSync(sessionId: String): SleepCheckIn?
    fun getAllCheckIns(): Flow<List<SleepCheckIn>>
    suspend fun getAllCheckInsSync(): List<SleepCheckIn>
    suspend fun saveCheckIn(checkIn: SleepCheckIn)
    suspend fun deleteForSession(sessionId: String)
    suspend fun deleteAll()
}

class SleepCheckInRepositoryImpl(
    private val dao: SleepCheckInDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SleepCheckInRepository {

    override fun getCheckInForSession(sessionId: String): Flow<SleepCheckIn?> {
        return dao.getCheckInForSession(sessionId).map { it?.toDomain() }
    }

    override suspend fun getCheckInForSessionSync(sessionId: String): SleepCheckIn? =
        withContext(ioDispatcher) {
            dao.getCheckInForSessionSync(sessionId)?.toDomain()
        }

    override fun getAllCheckIns(): Flow<List<SleepCheckIn>> {
        return dao.getAllCheckIns().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getAllCheckInsSync(): List<SleepCheckIn> =
        withContext(ioDispatcher) {
            dao.getAllCheckInsSync().map { it.toDomain() }
        }

    override suspend fun saveCheckIn(checkIn: SleepCheckIn) = withContext(ioDispatcher) {
        dao.insert(SleepCheckInEntity.fromDomain(checkIn))
    }

    override suspend fun deleteForSession(sessionId: String) = withContext(ioDispatcher) {
        dao.deleteForSession(sessionId)
    }

    override suspend fun deleteAll() = withContext(ioDispatcher) {
        dao.deleteAll()
    }
}
