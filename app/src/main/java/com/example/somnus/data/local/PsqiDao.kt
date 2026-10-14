package com.example.somnus.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PsqiDao {

    @Query("SELECT * FROM psqi_assessments ORDER BY startedAt DESC")
    fun getAllAssessments(): Flow<List<PsqiAssessmentEntity>>

    @Query("SELECT * FROM psqi_assessments WHERE id = :id LIMIT 1")
    fun getAssessmentById(id: String): Flow<PsqiAssessmentEntity?>

    @Query("SELECT * FROM psqi_assessments WHERE status = 'COMPLETED' ORDER BY completedAt DESC, startedAt DESC LIMIT 1")
    fun getLatestCompletedAssessment(): Flow<PsqiAssessmentEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(assessment: PsqiAssessmentEntity)

    @Update
    suspend fun update(assessment: PsqiAssessmentEntity)

    @Query("DELETE FROM psqi_assessments WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM psqi_assessments")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM psqi_assessments")
    suspend fun count(): Int
}
