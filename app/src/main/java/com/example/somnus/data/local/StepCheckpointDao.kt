package com.example.somnus.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StepCheckpointDao {

    @Query("SELECT * FROM step_checkpoints WHERE id = :id LIMIT 1")
    fun getCheckpoint(id: String = StepCounterCheckpointEntity.DEFAULT_CHECKPOINT_ID): Flow<StepCounterCheckpointEntity?>

    @Query("SELECT * FROM step_checkpoints WHERE id = :id LIMIT 1")
    suspend fun getCheckpointSync(id: String = StepCounterCheckpointEntity.DEFAULT_CHECKPOINT_ID): StepCounterCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checkpoint: StepCounterCheckpointEntity)

    @Query("DELETE FROM step_checkpoints WHERE id = :id")
    suspend fun delete(id: String = StepCounterCheckpointEntity.DEFAULT_CHECKPOINT_ID)

    @Query("DELETE FROM step_checkpoints")
    suspend fun deleteAll()
}
