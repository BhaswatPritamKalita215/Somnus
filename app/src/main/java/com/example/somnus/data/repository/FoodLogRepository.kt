package com.example.somnus.data.repository

import com.example.somnus.domain.model.DailyNutritionSummary
import com.example.somnus.domain.model.FoodLogEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface FoodLogRepository {
    fun getEntriesForDate(date: LocalDate): Flow<List<FoodLogEntry>>
    suspend fun getEntriesForDateSync(date: LocalDate): List<FoodLogEntry>
    fun getEntriesForDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<FoodLogEntry>>
    fun getDailySummary(date: LocalDate, calorieTarget: Int? = 2000): Flow<DailyNutritionSummary>
    fun getRecentEntries(limit: Int = 20): Flow<List<FoodLogEntry>>
    suspend fun logFood(entry: FoodLogEntry)
    suspend fun updateEntry(entry: FoodLogEntry)
    suspend fun getEntryById(id: String): FoodLogEntry?
    suspend fun deleteEntry(id: String)
    suspend fun deleteAll()
}
