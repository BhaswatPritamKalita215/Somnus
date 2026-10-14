package com.example.somnus.data.repository

import com.example.somnus.domain.analytics.AnalyticsReport
import com.example.somnus.domain.analytics.SleepDataQualityAnalyzer
import com.example.somnus.domain.analytics.SleepDateRange
import com.example.somnus.domain.analytics.SleepInsightEngine
import com.example.somnus.domain.analytics.SleepMetricsCalculator
import com.example.somnus.domain.analytics.SleepTrendAnalyzer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

class SleepAnalyticsRepositoryImpl(
    private val sessionRepository: SleepSessionRepository,
    private val solRepository: SolRepository,
    private val psqiRepository: PsqiRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : SleepAnalyticsRepository {

    override fun getAnalyticsReport(dateRange: SleepDateRange): Flow<AnalyticsReport> {
        val comparisonRange = dateRange.getPrecedingComparisonRange()

        return combine(
            sessionRepository.allSessions,
            solRepository.allRecords,
            psqiRepository.latestCompletedAssessment
        ) { sessions, solRecords, latestPsqi ->
            // 1. Current period calculation
            val currentDailySummaries = SleepMetricsCalculator.aggregateDailySummaries(
                sessions = sessions,
                solRecords = solRecords
            ).filter { !it.sleepDate.isBefore(dateRange.startDate) && !it.sleepDate.isAfter(dateRange.endDate) }

            val currentMetrics = SleepMetricsCalculator.calculatePeriodMetrics(
                dailySummaries = currentDailySummaries,
                dateRange = dateRange
            )

            // 2. Preceding comparison period calculation
            val previousDailySummaries = SleepMetricsCalculator.aggregateDailySummaries(
                sessions = sessions,
                solRecords = solRecords
            ).filter { !it.sleepDate.isBefore(comparisonRange.startDate) && !it.sleepDate.isAfter(comparisonRange.endDate) }

            val previousMetrics = SleepMetricsCalculator.calculatePeriodMetrics(
                dailySummaries = previousDailySummaries,
                dateRange = comparisonRange
            )

            // 3. Trend comparisons
            val comparisons = SleepTrendAnalyzer.comparePeriods(
                currentMetrics = currentMetrics,
                previousMetrics = previousMetrics,
                periodLabel = dateRange.label
            )

            // 4. Data quality analysis
            val dataQuality = SleepDataQualityAnalyzer.analyzeDataQuality(
                dailySummaries = currentDailySummaries,
                dateRange = dateRange
            )

            // 5. Deterministic insights
            val psqiScore = latestPsqi?.componentScores?.globalScore
            val insights = SleepInsightEngine.generateInsights(
                metrics = currentMetrics,
                comparisons = comparisons,
                dataQuality = dataQuality,
                rangeLabel = dateRange.label,
                latestPsqiScore = psqiScore
            )

            AnalyticsReport(
                selectedRange = dateRange,
                dailySummaries = currentDailySummaries,
                periodMetrics = currentMetrics,
                comparisons = comparisons,
                dataQuality = dataQuality,
                insights = insights,
                latestPsqiGlobalScore = psqiScore
            )
        }.flowOn(dispatcher)
    }
}
