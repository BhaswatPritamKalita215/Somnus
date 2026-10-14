package com.example.somnus.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local assistant messages.
 */
@Dao
interface AssistantMessageDao {

    @Query("SELECT * FROM assistant_messages ORDER BY timestampEpochMs ASC")
    fun observeAllMessages(): Flow<List<AssistantMessageEntity>>

    @Query("SELECT * FROM assistant_messages ORDER BY timestampEpochMs DESC LIMIT :limit")
    fun observeRecentMessages(limit: Int = 50): Flow<List<AssistantMessageEntity>>

    @Query("SELECT * FROM assistant_messages ORDER BY timestampEpochMs ASC")
    suspend fun getAllMessagesSync(): List<AssistantMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AssistantMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<AssistantMessageEntity>)

    @Query("DELETE FROM assistant_messages WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM assistant_messages")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM assistant_messages")
    suspend fun getMessageCount(): Int
}
