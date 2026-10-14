package com.example.somnus.data.repository

import com.example.somnus.data.local.StepCheckpointDao
import com.example.somnus.data.local.StepCounterCheckpointEntity
import com.example.somnus.data.local.StepRecordDao
import com.example.somnus.data.local.StepRecordEntity
import com.example.somnus.domain.analysis.StepReconciliationEngine
import com.example.somnus.domain.model.DailyStepSummary
import com.example.somnus.domain.model.StepCounterCheckpoint
import com.example.somnus.domain.model.StepRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate

class StepRepositoryImpl(
    private val stepRecordDao: StepRecordDao,
    private val stepCheckpointDao: StepCheckpointDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : StepRepository {

    override fun getStepRecordsForDate(date: LocalDate): Flow<List<StepRecord>> {
        return stepRecordDao.getRecordsForDate(date.toString()).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getStepRecordsForDateSync(date: LocalDate): List<StepRecord> =
        withContext(ioDispatcher) {
            stepRecordDao.getRecordsForDateSync(date.toString()).map { it.toDomain() }
        }

    override fun getStepRecordsForRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<StepRecord>> {
        return stepRecordDao.getRecordsForDateRange(startDate.toString(), endDate.toString())
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getStepRecordsForRangeSync(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<StepRecord> = withContext(ioDispatcher) {
        stepRecordDao.getRecordsForDateRangeSync(startDate.toString(), endDate.toString())
            .map { it.toDomain() }
    }

    override suspend fun insertRecord(record: StepRecord) = withContext(ioDispatcher) {
        stepRecordDao.insert(StepRecordEntity.fromDomain(record))
    }

    override suspend fun insertRecords(records: List<StepRecord>) = withContext(ioDispatcher) {
        stepRecordDao.insertAll(records.map { StepRecordEntity.fromDomain(it) })
    }

    override suspend fun deleteRecordByExternalId(externalRecordId: String) =
        withContext(ioDispatcher) {
            stepRecordDao.deleteByExternalId(externalRecordId)
        }

    override suspend fun deleteAll() = withContext(ioDispatcher) {
        stepRecordDao.deleteAll()
        stepCheckpointDao.deleteAll()
    }

    override fun getCheckpoint(): Flow<StepCounterCheckpoint?> {
        return stepCheckpointDao.getCheckpoint().map { it?.toDomain() }
    }

    override suspend fun getCheckpointSync(): StepCounterCheckpoint? = withContext(ioDispatcher) {
        stepCheckpointDao.getCheckpointSync()?.toDomain()
    }

    override suspend fun saveCheckpoint(checkpoint: StepCounterCheckpoint) =
        withContext(ioDispatcher) {
            stepCheckpointDao.insert(StepCounterCheckpointEntity.fromDomain(checkpoint))
        }

    override fun getDailySummary(date: LocalDate, goalSteps: Int): Flow<DailyStepSummary> {
        return getStepRecordsForDate(date).map { records ->
            StepReconciliationEngine.reconcileDailySteps(date, records, goalSteps)
        }
    }

    override suspend fun getDailySummarySync(date: LocalDate, goalSteps: Int): DailyStepSummary =
        withContext(ioDispatcher) {
            val records = getStepRecordsForDateSync(date)
            StepReconciliationEngine.reconcileDailySteps(date, records, goalSteps)
        }

    override fun getDailySummariesForRange(
        startDate: LocalDate,
        endDate: LocalDate,
        goalSteps: Int
    ): Flow<List<DailyStepSummary>> {
        return getStepRecordsForRange(startDate, endDate).map { records ->
            val summaryMap = StepReconciliationEngine.reconcileDateRange(
                startDate = startDate,
                endDate = endDate,
                records = records,
                goalSteps = goalSteps
            )
            summaryMap.values.sortedBy { it.date }
        }
    }
}
