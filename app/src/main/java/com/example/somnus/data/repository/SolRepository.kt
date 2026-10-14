package com.example.somnus.data.repository

import com.example.somnus.domain.model.SolRecord
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface SolRepository {
    val allRecords: Flow<List<SolRecord>>
    fun getRecordsForDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<SolRecord>>
    fun getRecordById(id: String): Flow<SolRecord?>
    fun getRecordsForSession(sessionId: String): Flow<List<SolRecord>>
    suspend fun saveRecord(record: SolRecord): Result<Unit>
    suspend fun updateRecord(record: SolRecord): Result<Unit>
    suspend fun deleteRecord(id: String): Result<Unit>
    suspend fun deleteAll(): Result<Unit>
}
