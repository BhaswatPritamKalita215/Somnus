package com.example.somnus.data.repository

import com.example.somnus.domain.model.DailyStepSummary
import com.example.somnus.domain.model.StepCounterCheckpoint
import com.example.somnus.domain.model.StepRecord
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Repository interface defining step and physical activity data access operations.
 * Upholds local-first persistence, provenance attribution, and transparent multi-source reconciliation.
 */
interface StepRepository {

    /**
     * Observes raw step records recorded or imported for the specified [date].
     */
    fun getStepRecordsForDate(date: LocalDate): Flow<List<StepRecord>>

    /**
     * Synchronously queries step records for the specified [date].
     */
    suspend fun getStepRecordsForDateSync(date: LocalDate): List<StepRecord>

    /**
     * Observes raw step records across an inclusive [startDate] to [endDate] range.
     */
    fun getStepRecordsForRange(startDate: LocalDate, endDate: LocalDate): Flow<List<StepRecord>>

    /**
     * Synchronously queries step records across an inclusive [startDate] to [endDate] range.
     */
    suspend fun getStepRecordsForRangeSync(startDate: LocalDate, endDate: LocalDate): List<StepRecord>

    /**
     * Inserts or replaces a raw step record.
     */
    suspend fun insertRecord(record: StepRecord)

    /**
     * Inserts or replaces a collection of raw step records atomically.
     */
    suspend fun insertRecords(records: List<StepRecord>)

    /**
     * Deletes a record by its external record ID (e.g. Health Connect UID).
     */
    suspend fun deleteRecordByExternalId(externalRecordId: String)

    /**
     * Deletes all step records and checkpoints.
     */
    suspend fun deleteAll()

    /**
     * Observes the active hardware step counter checkpoint.
     */
    fun getCheckpoint(): Flow<StepCounterCheckpoint?>

    /**
     * Synchronously queries the active hardware step counter checkpoint.
     */
    suspend fun getCheckpointSync(): StepCounterCheckpoint?

    /**
     * Persists a hardware step counter checkpoint.
     */
    suspend fun saveCheckpoint(checkpoint: StepCounterCheckpoint)

    /**
     * Observes a fully reconciled [DailyStepSummary] for the given [date] against [goalSteps].
     */
    fun getDailySummary(date: LocalDate, goalSteps: Int): Flow<DailyStepSummary>

    /**
     * Synchronously computes a reconciled [DailyStepSummary] for the given [date] against [goalSteps].
     */
    suspend fun getDailySummarySync(date: LocalDate, goalSteps: Int): DailyStepSummary

    /**
     * Observes a list of reconciled daily step summaries across an inclusive date range.
     */
    fun getDailySummariesForRange(startDate: LocalDate, endDate: LocalDate, goalSteps: Int): Flow<List<DailyStepSummary>>
}
