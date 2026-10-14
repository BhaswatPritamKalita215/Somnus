package com.example.somnus.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StepRecordDao {

    @Query("SELECT * FROM step_records WHERE localDate = :localDate ORDER BY startTime ASC")
    fun getRecordsForDate(localDate: String): Flow<List<StepRecordEntity>>

    @Query("SELECT * FROM step_records WHERE localDate = :localDate ORDER BY startTime ASC")
    suspend fun getRecordsForDateSync(localDate: String): List<StepRecordEntity>

    @Query("SELECT * FROM step_records WHERE localDate >= :startDate AND localDate <= :endDate ORDER BY localDate ASC, startTime ASC")
    fun getRecordsForDateRange(startDate: String, endDate: String): Flow<List<StepRecordEntity>>

    @Query("SELECT * FROM step_records WHERE localDate >= :startDate AND localDate <= :endDate ORDER BY localDate ASC, startTime ASC")
    suspend fun getRecordsForDateRangeSync(startDate: String, endDate: String): List<StepRecordEntity>

    @Query("SELECT * FROM step_records WHERE externalRecordId = :externalRecordId LIMIT 1")
    suspend fun getRecordByExternalId(externalRecordId: String): StepRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: StepRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<StepRecordEntity>)

    @Query("DELETE FROM step_records WHERE externalRecordId = :externalRecordId")
    suspend fun deleteByExternalId(externalRecordId: String)

    @Query("DELETE FROM step_records")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM step_records")
    suspend fun count(): Int
}
