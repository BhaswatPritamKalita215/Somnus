package com.example.somnus.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepCheckInDao {

    @Query("SELECT * FROM sleep_check_ins WHERE sessionId = :sessionId LIMIT 1")
    fun getCheckInForSession(sessionId: String): Flow<SleepCheckInEntity?>

    @Query("SELECT * FROM sleep_check_ins WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getCheckInForSessionSync(sessionId: String): SleepCheckInEntity?

    @Query("SELECT * FROM sleep_check_ins ORDER BY createdAt DESC")
    fun getAllCheckIns(): Flow<List<SleepCheckInEntity>>

    @Query("SELECT * FROM sleep_check_ins ORDER BY createdAt DESC")
    suspend fun getAllCheckInsSync(): List<SleepCheckInEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checkIn: SleepCheckInEntity)

    @Query("DELETE FROM sleep_check_ins WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Query("DELETE FROM sleep_check_ins")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM sleep_check_ins")
    suspend fun count(): Int
}
