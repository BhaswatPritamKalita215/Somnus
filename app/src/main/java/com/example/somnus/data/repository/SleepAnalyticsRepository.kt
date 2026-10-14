package com.example.somnus.data.repository

import com.example.somnus.domain.analytics.AnalyticsReport
import com.example.somnus.domain.analytics.SleepDateRange
import kotlinx.coroutines.flow.Flow

/**
 * Repository coordinating sleep analytics computations across sessions, SOL records, and PSQI assessments.
 */
interface SleepAnalyticsRepository {

    /**
     * Observes real-time reactive analytics for a specified [SleepDateRange].
     * Recalculates automatically whenever underlying sessions or records change.
     */
    fun getAnalyticsReport(dateRange: SleepDateRange): Flow<AnalyticsReport>
}
