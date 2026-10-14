package com.example.somnus.data.repository

import com.example.somnus.data.local.SolRecordDao
import com.example.somnus.data.local.SolRecordEntity
import com.example.somnus.domain.model.SolRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class SolRepositoryImpl(
    private val dao: SolRecordDao
) : SolRepository {

    override val allRecords: Flow<List<SolRecord>> = dao.getAllRecords().map { entities ->
        entities.map { it.toDomain() }
    }

    override fun getRecordsForDateRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<SolRecord>> {
        return dao.getRecordsForDateRange(startDate.toString(), endDate.toString()).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getRecordById(id: String): Flow<SolRecord?> {
        return dao.getRecordById(id).map { it?.toDomain() }
    }

    override fun getRecordsForSession(sessionId: String): Flow<List<SolRecord>> {
        return dao.getRecordsForSession(sessionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveRecord(record: SolRecord): Result<Unit> {
        return runCatching {
            dao.insert(SolRecordEntity.fromDomain(record))
        }
    }

    override suspend fun updateRecord(record: SolRecord): Result<Unit> {
        return runCatching {
            dao.update(SolRecordEntity.fromDomain(record))
        }
    }

    override suspend fun deleteRecord(id: String): Result<Unit> {
        return runCatching {
            dao.deleteById(id)
        }
    }

    override suspend fun deleteAll(): Result<Unit> {
        return runCatching {
            dao.deleteAll()
        }
    }
}
