package com.example.somnus.data.repository

import com.example.somnus.data.local.PsqiAssessmentEntity
import com.example.somnus.data.local.PsqiDao
import com.example.somnus.domain.model.PsqiAssessment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PsqiRepositoryImpl(
    private val dao: PsqiDao
) : PsqiRepository {

    override val allAssessments: Flow<List<PsqiAssessment>> = dao.getAllAssessments().map { entities ->
        entities.map { it.toDomain() }
    }

    override val latestCompletedAssessment: Flow<PsqiAssessment?> = dao.getLatestCompletedAssessment().map {
        it?.toDomain()
    }

    override fun getAssessmentById(id: String): Flow<PsqiAssessment?> {
        return dao.getAssessmentById(id).map { it?.toDomain() }
    }

    override suspend fun saveAssessment(assessment: PsqiAssessment): Result<Unit> {
        return runCatching {
            dao.insert(PsqiAssessmentEntity.fromDomain(assessment))
        }
    }

    override suspend fun updateAssessment(assessment: PsqiAssessment): Result<Unit> {
        return runCatching {
            dao.update(PsqiAssessmentEntity.fromDomain(assessment))
        }
    }

    override suspend fun deleteAssessment(id: String): Result<Unit> {
        return runCatching {
            dao.deleteById(id)
        }
    }

    override suspend fun deleteAll(): Result<Unit> {
        return runCatching {
            dao.deleteAll()
        }
    }
}
