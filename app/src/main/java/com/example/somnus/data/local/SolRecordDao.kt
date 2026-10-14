package com.example.somnus.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SolRecordDao {

    @Query("SELECT * FROM sol_records ORDER BY reportingDate DESC, createdAt DESC")
    fun getAllRecords(): Flow<List<SolRecordEntity>>

    @Query("SELECT * FROM sol_records WHERE reportingDate >= :startDate AND reportingDate <= :endDate ORDER BY reportingDate ASC, createdAt ASC")
    fun getRecordsForDateRange(startDate: String, endDate: String): Flow<List<SolRecordEntity>>

    @Query("SELECT * FROM sol_records WHERE id = :id LIMIT 1")
    fun getRecordById(id: String): Flow<SolRecordEntity?>

    @Query("SELECT * FROM sol_records WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun getRecordsForSession(sessionId: String): Flow<List<SolRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: SolRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<SolRecordEntity>)

    @Update
    suspend fun update(record: SolRecordEntity)

    @Query("DELETE FROM sol_records WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM sol_records")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM sol_records")
    suspend fun count(): Int
}
