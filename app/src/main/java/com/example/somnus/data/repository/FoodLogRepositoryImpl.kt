package com.example.somnus.data.repository

import com.example.somnus.data.local.FoodLogDao
import com.example.somnus.data.local.FoodLogEntryEntity
import com.example.somnus.domain.model.DailyNutritionSummary
import com.example.somnus.domain.model.FoodLogEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate

class FoodLogRepositoryImpl(
    private val foodLogDao: FoodLogDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : FoodLogRepository {

    override fun getEntriesForDate(date: LocalDate): Flow<List<FoodLogEntry>> {
        return foodLogDao.getEntriesForDate(date.toString()).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getEntriesForDateSync(date: LocalDate): List<FoodLogEntry> =
        withContext(ioDispatcher) {
            foodLogDao.getEntriesForDateSync(date.toString()).map { it.toDomain() }
        }

    override fun getEntriesForDateRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<FoodLogEntry>> {
        return foodLogDao.getEntriesForDateRange(startDate.toString(), endDate.toString()).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getDailySummary(
        date: LocalDate,
        calorieTarget: Int?
    ): Flow<DailyNutritionSummary> {
        return getEntriesForDate(date).map { entries ->
            val totalCalories = entries.sumOf { it.calculatedCalories }
            val totalProtein = entries.mapNotNull { it.calculatedProtein }.sum()
            val totalCarbs = entries.mapNotNull { it.calculatedCarbs }.sum()
            val totalFat = entries.mapNotNull { it.calculatedFat }.sum()

            DailyNutritionSummary(
                date = date,
                totalCalories = totalCalories,
                totalProtein = totalProtein,
                totalCarbs = totalCarbs,
                totalFat = totalFat,
                entries = entries,
                calorieTarget = calorieTarget,
                isLoggingComplete = entries.isNotEmpty()
            )
        }
    }

    override fun getRecentEntries(limit: Int): Flow<List<FoodLogEntry>> {
        return foodLogDao.getRecentEntries(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun logFood(entry: FoodLogEntry) = withContext(ioDispatcher) {
        foodLogDao.insert(FoodLogEntryEntity.fromDomain(entry))
    }

    override suspend fun deleteEntry(id: String) = withContext(ioDispatcher) {
        foodLogDao.deleteById(id)
    }

    override suspend fun deleteAll() = withContext(ioDispatcher) {
        foodLogDao.deleteAll()
    }
}
