package com.example.somnus.data.repository

import com.example.somnus.domain.model.PsqiAssessment
import kotlinx.coroutines.flow.Flow

interface PsqiRepository {
    val allAssessments: Flow<List<PsqiAssessment>>
    val latestCompletedAssessment: Flow<PsqiAssessment?>
    fun getAssessmentById(id: String): Flow<PsqiAssessment?>
    suspend fun saveAssessment(assessment: PsqiAssessment): Result<Unit>
    suspend fun updateAssessment(assessment: PsqiAssessment): Result<Unit>
    suspend fun deleteAssessment(id: String): Result<Unit>
    suspend fun deleteAll(): Result<Unit>
}
