package com.example.somnus.domain.dashboard

import com.example.somnus.data.healthconnect.HealthConnectEnergyExpenditure
import com.example.somnus.domain.analytics.DaySleepSummary
import com.example.somnus.domain.model.DailyNutritionSummary
import com.example.somnus.domain.model.DailyStepSummary
import com.example.somnus.domain.model.PsqiAssessment
import com.example.somnus.domain.model.SleepSession
import com.example.somnus.domain.model.SolRecord
import java.time.LocalDate

/**
 * Metric categories compared in cross-metric correlation analysis.
 */
enum class CrossMetricType(val displayName: String) {
    ACTIVITY_VS_SLEEP_DURATION("Activity & Sleep Duration"),
    ACTIVITY_VS_BEDTIME("Activity & Bedtime Schedule"),
    ENERGY_BALANCE("Nutrition Intake & Energy Expenditure"),
    MULTI_DAY_TREND("Multi-Metric Rolling Trends")
}

/**
 * Level of statistical confidence for a cross-metric observation.
 */
enum class InsightConfidence(val displayName: String, val badgeColorName: String) {
    SUFFICIENT_DATA("Sufficient Data", "Primary"),
    PRELIMINARY("Preliminary (Small Sample)", "Tertiary"),
    INSUFFICIENT("Insufficient Data", "Outline")
}

/**
 * Transparent, evidence-qualified cross-metric analytical insight.
 *
 * Guarantees:
 * 1. Association only: Strictly avoids claiming causal links.
 * 2. Real data: Generated only from actual stored database records.
 * 3. Qualified minimum sample size: Requires a documented minimum number of observations.
 * 4. Plain language: Explains the underlying comparison clearly.
 */
data class CrossMetricInsight(
    val id: String,
    val type: CrossMetricType,
    val title: String,
    val headline: String,
    val explanation: String,
    val caveat: String = "Statistical association in your recorded data — does not imply causation.",
    val validDaysCount: Int,
    val minimumDaysRequired: Int,
    val confidence: InsightConfidence,
    val isStatisticallyReliable: Boolean = confidence == InsightConfidence.SUFFICIENT_DATA,
    val metricsInvolved: List<String> = emptyList()
)

/**
 * Data coverage completeness tier across health domains.
 */
enum class CoverageCompletenessTier(val displayName: String) {
    COMPREHENSIVE("Comprehensive"),
    MODERATE("Moderate"),
    SPARSE("Sparse"),
    MINIMAL("Minimal")
}

/**
 * Data coverage and quality summary over a multi-day inspection window.
 */
data class DataCoverageSummary(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalDays: Int,
    val sleepDaysWithData: Int,
    val stepDaysWithData: Int,
    val nutritionDaysWithData: Int,
    val energyDaysWithData: Int,
    val completenessTier: CoverageCompletenessTier,
    val summaryText: String
) {
    val overallCoveragePercentage: Float
        get() {
            if (totalDays <= 0) return 0f
            val totalSlots = totalDays * 4f // 4 domains: sleep, steps, nutrition, energy
            val filledSlots = sleepDaysWithData + stepDaysWithData + nutritionDaysWithData + energyDaysWithData
            return ((filledSlots / totalSlots) * 100f).coerceIn(0f, 100f)
        }
}

/**
 * Unified health metrics snapshot for a specific calendar date.
 */
data class DashboardDayData(
    val selectedDate: LocalDate,
    val sleepSummary: DaySleepSummary? = null,
    val primarySleepSession: SleepSession? = null,
    val allSleepSessionsForDate: List<SleepSession> = emptyList(),
    val solRecord: SolRecord? = null,
    val latestPsqi: PsqiAssessment? = null,
    val stepSummary: DailyStepSummary? = null,
    val nutritionSummary: DailyNutritionSummary? = null,
    val energyExpenditure: HealthConnectEnergyExpenditure? = null,
    val hasHealthConnectEnergyPermissions: Boolean = false,
    val activeTrackingSession: SleepSession? = null,
    val isSessionStale: Boolean = false,
    val stepGoal: Int = 8000,
    val calorieTarget: Int = 2000
) {
    /**
     * Identifies whether any metrics were recorded for this calendar date.
     */
    val hasAnyData: Boolean
        get() = sleepSummary != null ||
                (stepSummary != null && stepSummary.totalSteps > 0) ||
                (nutritionSummary != null && nutritionSummary.entries.isNotEmpty()) ||
                (energyExpenditure != null && (energyExpenditure.activeCaloriesKcal != null || energyExpenditure.totalCaloriesKcal != null))
}
