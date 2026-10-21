package com.example.somnus.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodLogDao {

    @Query("SELECT * FROM food_log_entries WHERE localDate = :localDate ORDER BY loggedAt ASC")
    fun getEntriesForDate(localDate: String): Flow<List<FoodLogEntryEntity>>

    @Query("SELECT * FROM food_log_entries WHERE localDate = :localDate ORDER BY loggedAt ASC")
    suspend fun getEntriesForDateSync(localDate: String): List<FoodLogEntryEntity>

    @Query("SELECT * FROM food_log_entries WHERE localDate >= :startDate AND localDate <= :endDate ORDER BY localDate ASC, loggedAt ASC")
    fun getEntriesForDateRange(startDate: String, endDate: String): Flow<List<FoodLogEntryEntity>>

    @Query("SELECT * FROM food_log_entries WHERE localDate >= :startDate AND localDate <= :endDate ORDER BY localDate ASC, loggedAt ASC")
    suspend fun getEntriesForDateRangeSync(startDate: String, endDate: String): List<FoodLogEntryEntity>

    @Query("SELECT * FROM food_log_entries ORDER BY loggedAt DESC LIMIT :limit")
    fun getRecentEntries(limit: Int = 20): Flow<List<FoodLogEntryEntity>>

    @Query("SELECT * FROM food_log_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: String): FoodLogEntryEntity?

    @Query("SELECT * FROM food_log_entries WHERE externalRecordId = :externalRecordId LIMIT 1")
    suspend fun getEntryByExternalRecordId(externalRecordId: String): FoodLogEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: FoodLogEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<FoodLogEntryEntity>)

    @Update
    suspend fun update(entry: FoodLogEntryEntity)

    @Query("DELETE FROM food_log_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM food_log_entries WHERE externalRecordId = :externalRecordId")
    suspend fun deleteByExternalRecordId(externalRecordId: String)

    @Query("DELETE FROM food_log_entries")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM food_log_entries")
    suspend fun count(): Int
}
