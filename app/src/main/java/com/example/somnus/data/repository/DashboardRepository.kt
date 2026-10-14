package com.example.somnus.data.repository

import com.example.somnus.data.healthconnect.HealthConnectEnergyExpenditure
import com.example.somnus.domain.dashboard.CrossMetricInsight
import com.example.somnus.domain.dashboard.DashboardDayData
import com.example.somnus.domain.dashboard.DataCoverageSummary
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Unified repository providing reactive aggregation across sleep, activity, nutrition, and energy domains.
 */
interface DashboardRepository {

    /**
     * Observes the unified health snapshot for a specific calendar date.
     */
    fun observeDashboardDay(date: LocalDate): Flow<DashboardDayData>

    /**
     * Observes cross-metric correlations over a historical date window.
     */
    fun observeCrossMetricInsights(startDate: LocalDate, endDate: LocalDate): Flow<List<CrossMetricInsight>>

    /**
     * Observes the multi-domain data coverage and completeness summary over a date window.
     */
    fun observeDataCoverage(startDate: LocalDate, endDate: LocalDate): Flow<DataCoverageSummary>
}
