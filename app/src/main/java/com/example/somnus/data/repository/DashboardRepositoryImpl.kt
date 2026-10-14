package com.example.somnus.data.repository

import com.example.somnus.data.healthconnect.HealthConnectEnergyExpenditure
import com.example.somnus.data.healthconnect.HealthConnectManager
import com.example.somnus.data.preferences.UserPreferencesRepository
import com.example.somnus.domain.analysis.NutritionCalculator
import com.example.somnus.domain.analytics.SleepMetricsCalculator
import com.example.somnus.domain.dashboard.CoverageCompletenessTier
import com.example.somnus.domain.dashboard.CrossMetricInsight
import com.example.somnus.domain.dashboard.CrossMetricInsightEngine
import com.example.somnus.domain.dashboard.DashboardDayData
import com.example.somnus.domain.dashboard.DataCoverageSummary
import com.example.somnus.session.SessionManager
import com.example.somnus.domain.model.PsqiAssessment
import com.example.somnus.domain.model.SleepSession
import com.example.somnus.domain.model.SolRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private data class SleepCombinedState(
    val completedSessions: List<SleepSession>,
    val activeSession: SleepSession?,
    val solRecord: SolRecord?,
    val latestPsqi: PsqiAssessment?
)

/**
 * Production implementation of [DashboardRepository].
 *
 * Guarantees:
 * 1. Off-thread calculation: All multi-domain joining and circular stats run on [ioDispatcher].
 * 2. Reactive freshness: Any edit, deletion, or logging in sleep, steps, or food immediately updates the dashboard.
 * 3. Consistent date convention: Clinical sleep attribution ensures midnight-crossing sessions belong to the proper sleep day.
 * 4. Honest reporting: Does not conflate missing sensor data with zeros.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardRepositoryImpl(
    private val sleepSessionRepository: SleepSessionRepository,
    private val solRepository: SolRepository,
    private val psqiRepository: PsqiRepository,
    private val stepRepository: StepRepository,
    private val foodLogRepository: FoodLogRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val sessionManager: SessionManager,
    private val healthConnectManager: HealthConnectManager? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DashboardRepository {

    override fun observeDashboardDay(date: LocalDate): Flow<DashboardDayData> {
        val completedSessionsFlow = sleepSessionRepository.completedSessions
        val activeSessionFlow = sessionManager.activeSession
        val solFlow = solRepository.getRecordsForDateRange(date, date).map { it.firstOrNull() }
        val psqiFlow = psqiRepository.latestCompletedAssessment
        val foodEntriesFlow = foodLogRepository.getEntriesForDate(date)

        val sleepCombinedFlow = combine(
            completedSessionsFlow,
            activeSessionFlow,
            solFlow,
            psqiFlow
        ) { completed, active, sol, psqi ->
            SleepCombinedState(completed, active, sol, psqi)
        }

        return userPreferencesRepository.userPreferencesFlow.flatMapLatest { prefs ->
            val stepSummaryFlow = stepRepository.getDailySummary(date, prefs.dailyStepGoal)

            combine(
                sleepCombinedFlow,
                foodEntriesFlow,
                stepSummaryFlow
            ) { sleepState, foodEntries, stepSummary ->
                val dateSessions = sleepState.completedSessions.filter { session ->
                    SleepMetricsCalculator.determineSleepDate(session) == date
                }

                val allSummaries = SleepMetricsCalculator.aggregateDailySummaries(dateSessions, listOfNotNull(sleepState.solRecord))
                val sleepSummary = allSummaries.find { it.sleepDate == date }

                val primarySession = dateSessions.maxByOrNull {
                    Duration.between(it.startTime, it.endTime ?: it.startTime).toMillis()
                }

                val nutritionSummary = NutritionCalculator.calculateDailySummary(
                    date = date,
                    entries = foodEntries,
                    calorieTarget = prefs.dailyCalorieTarget
                )

                val energy = fetchEnergyExpenditureSafely(date)
                val hasEnergyPerms = checkEnergyPermissionsSafely()

                DashboardDayData(
                    selectedDate = date,
                    sleepSummary = sleepSummary,
                    primarySleepSession = primarySession,
                    allSleepSessionsForDate = dateSessions,
                    solRecord = sleepState.solRecord,
                    latestPsqi = sleepState.latestPsqi,
                    stepSummary = stepSummary,
                    nutritionSummary = nutritionSummary,
                    energyExpenditure = energy,
                    hasHealthConnectEnergyPermissions = hasEnergyPerms,
                    activeTrackingSession = sleepState.activeSession,
                    isSessionStale = sessionManager.isCurrentSessionStale(),
                    stepGoal = prefs.dailyStepGoal,
                    calorieTarget = prefs.dailyCalorieTarget
                )
            }.flowOn(ioDispatcher)
        }
    }

    override fun observeCrossMetricInsights(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<CrossMetricInsight>> {
        val completedSessionsFlow = sleepSessionRepository.completedSessions
        val solFlow = solRepository.getRecordsForDateRange(startDate, endDate)
        val foodEntriesFlow = foodLogRepository.getEntriesForDateRange(startDate, endDate)

        return userPreferencesRepository.userPreferencesFlow.flatMapLatest { prefs ->
            val stepSummariesFlow = stepRepository.getDailySummariesForRange(startDate, endDate, prefs.dailyStepGoal)

            combine(
                completedSessionsFlow,
                solFlow,
                stepSummariesFlow,
                foodEntriesFlow
            ) { sessions, solRecords, stepSummaries, foodEntries ->
                val relevantSessions = sessions.filter {
                    val sleepDate = SleepMetricsCalculator.determineSleepDate(it)
                    sleepDate in startDate..endDate
                }

                val dailySleepSummaries = SleepMetricsCalculator.aggregateDailySummaries(
                    sessions = relevantSessions,
                    solRecords = solRecords
                )

                val dailyNutritionSummaries = foodEntries
                    .groupBy { it.localDate }
                    .map { (date, entries) ->
                        NutritionCalculator.calculateDailySummary(
                            date = date,
                            entries = entries,
                            calorieTarget = prefs.dailyCalorieTarget
                        )
                    }

                // Collect energy expenditures for dates with logged food
                val energyMap = mutableMapOf<LocalDate, HealthConnectEnergyExpenditure>()
                dailyNutritionSummaries.forEach { nSummary ->
                    val energy = fetchEnergyExpenditureSafely(nSummary.date)
                    if (energy != null) {
                        energyMap[nSummary.date] = energy
                    }
                }

                CrossMetricInsightEngine.evaluateCrossMetricInsights(
                    sleepSummaries = dailySleepSummaries,
                    stepSummaries = stepSummaries,
                    nutritionSummaries = dailyNutritionSummaries,
                    energyExpenditures = energyMap
                )
            }.flowOn(ioDispatcher)
        }
    }

    override fun observeDataCoverage(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<DataCoverageSummary> {
        val completedSessionsFlow = sleepSessionRepository.completedSessions
        val foodEntriesFlow = foodLogRepository.getEntriesForDateRange(startDate, endDate)

        return userPreferencesRepository.userPreferencesFlow.flatMapLatest { prefs ->
            val stepSummariesFlow = stepRepository.getDailySummariesForRange(startDate, endDate, prefs.dailyStepGoal)

            combine(
                completedSessionsFlow,
                stepSummariesFlow,
                foodEntriesFlow
            ) { sessions, stepSummaries, foodEntries ->
                val totalDays = (ChronoUnit.DAYS.between(startDate, endDate) + 1).toInt().coerceAtLeast(1)

                val sleepDatesWithData = sessions
                    .map { SleepMetricsCalculator.determineSleepDate(it) }
                    .filter { it in startDate..endDate }
                    .distinct()
                    .size

                val stepDatesWithData = stepSummaries
                    .filter { it.totalSteps > 0 && it.date in startDate..endDate }
                    .map { it.date }
                    .distinct()
                    .size

                val nutritionDatesWithData = foodEntries
                    .map { it.localDate }
                    .filter { it in startDate..endDate }
                    .distinct()
                    .size

                // Check energy expenditure days
                var energyDaysWithData = 0
                val checkLimit = minOf(totalDays, 14) // Limit network/binder queries to max 14 days
                for (offset in 0 until checkLimit) {
                    val date = endDate.minusDays(offset.toLong())
                    if (date >= startDate) {
                        val energy = fetchEnergyExpenditureSafely(date)
                        if (energy?.activeCaloriesKcal != null || energy?.totalCaloriesKcal != null) {
                            energyDaysWithData++
                        }
                    }
                }

                val totalSlots = totalDays * 4f
                val filledSlots = sleepDatesWithData + stepDatesWithData + nutritionDatesWithData + energyDaysWithData
                val coveragePercent = ((filledSlots / totalSlots) * 100f).coerceIn(0f, 100f)

                val tier = when {
                    coveragePercent >= 75f -> CoverageCompletenessTier.COMPREHENSIVE
                    coveragePercent >= 50f -> CoverageCompletenessTier.MODERATE
                    coveragePercent >= 25f -> CoverageCompletenessTier.SPARSE
                    else -> CoverageCompletenessTier.MINIMAL
                }

                val summaryText = when (tier) {
                    CoverageCompletenessTier.COMPREHENSIVE ->
                        "Comprehensive health tracking across sleep, activity, and nutrition ($totalDays-day window)."
                    CoverageCompletenessTier.MODERATE ->
                        "Moderate data recorded. Tracking multiple domains consistently will enhance cross-metric accuracy."
                    CoverageCompletenessTier.SPARSE ->
                        "Sparse logging detected. Days with unrecorded metrics reflect unmeasured intervals rather than zero values."
                    CoverageCompletenessTier.MINIMAL ->
                        "Early tracking phase. Record sleep sessions, carry phone for steps, or log snacks to unlock correlation insights."
                }

                DataCoverageSummary(
                    startDate = startDate,
                    endDate = endDate,
                    totalDays = totalDays,
                    sleepDaysWithData = sleepDatesWithData,
                    stepDaysWithData = stepDatesWithData,
                    nutritionDaysWithData = nutritionDatesWithData,
                    energyDaysWithData = energyDaysWithData,
                    completenessTier = tier,
                    summaryText = summaryText
                )
            }.flowOn(ioDispatcher)
        }
    }

    private suspend fun fetchEnergyExpenditureSafely(date: LocalDate): HealthConnectEnergyExpenditure? {
        val manager = healthConnectManager ?: return null
        return try {
            if (manager.hasEnergyExpenditurePermissions()) {
                val zone = ZoneId.systemDefault()
                val start = date.atStartOfDay(zone).toInstant()
                val end = date.plusDays(1).atStartOfDay(zone).toInstant()
                manager.getEnergyExpenditure(start, end)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun checkEnergyPermissionsSafely(): Boolean {
        val manager = healthConnectManager ?: return false
        return try {
            manager.hasEnergyExpenditurePermissions()
        } catch (_: Exception) {
            false
        }
    }
}
