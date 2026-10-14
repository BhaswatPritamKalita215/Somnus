package com.example.somnus.domain.analytics

/**
 * Deterministic, transparent rules-based insight engine.
 *
 * All insights are strictly generated from persisted records and traceable calculations.
 * Features calm, nonjudgmental phrasing without speculative causal claims or medical diagnosis.
 */
object SleepInsightEngine {

    const val MIN_OBSERVATIONS_FOR_INSIGHTS = 3

    fun generateInsights(
        metrics: PeriodSleepMetrics,
        comparisons: List<MetricComparison>,
        dataQuality: SleepDataQuality,
        rangeLabel: String,
        latestPsqiScore: Int? = null
    ): List<SleepInsight> {
        val insights = mutableListOf<SleepInsight>()

        // 1. Data Coverage & Sufficiency Assessment
        if (metrics.totalNightsTracked < MIN_OBSERVATIONS_FOR_INSIGHTS) {
            insights.add(
                SleepInsight(
                    category = InsightCategory.DATA_COVERAGE,
                    title = "Building Your Baseline",
                    observation = "Recorded ${metrics.totalNightsTracked} of ${dataQuality.totalDays} nights in $rangeLabel.",
                    explanation = "Trend calculations require at least $MIN_OBSERVATIONS_FOR_INSIGHTS recorded nights to yield reliable statistics and avoid over-interpreting isolated nights.",
                    evidenceLevel = EvidenceLevel.PRELIMINARY
                )
            )
            // Suppress remaining insights if observations are insufficient
            return insights
        }

        if (dataQuality.qualityLevel == DataQualityLevel.EXCELLENT) {
            insights.add(
                SleepInsight(
                    category = InsightCategory.DATA_COVERAGE,
                    title = "Strong Tracking Coverage",
                    observation = "Logged ${dataQuality.nightsWithUsableData} of ${dataQuality.totalDays} nights (${dataQuality.coveragePercentage}% coverage).",
                    explanation = "High tracking coverage provides an accurate representation of your sleep patterns without missing-data bias.",
                    evidenceLevel = EvidenceLevel.ROBUST
                )
            )
        }

        // 2. Schedule Consistency Insights
        val bedtimeVar = metrics.bedtimeVariabilityMinutes
        if (bedtimeVar != null) {
            if (bedtimeVar <= 30.0) {
                insights.add(
                    SleepInsight(
                        category = InsightCategory.CONSISTENCY,
                        title = "Consistent Bedtime Schedule",
                        observation = "Bedtime timing varied by approximately ${bedtimeVar.toInt()} minutes across recorded nights.",
                        explanation = "Calculated using circular standard deviation to accurately account for nights crossing midnight.",
                        evidenceLevel = if (metrics.totalNightsTracked >= 5) EvidenceLevel.ROBUST else EvidenceLevel.MODERATE
                    )
                )
            } else if (bedtimeVar >= 75.0) {
                insights.add(
                    SleepInsight(
                        category = InsightCategory.CONSISTENCY,
                        title = "Variable Bedtime Schedule",
                        observation = "Bedtime shifted by an average of ${bedtimeVar.toInt()} minutes from night to night.",
                        explanation = "A spread greater than 60 minutes indicates varying schedules across weekdays or weekends.",
                        evidenceLevel = if (metrics.totalNightsTracked >= 5) EvidenceLevel.ROBUST else EvidenceLevel.MODERATE
                    )
                )
            }
        }

        // 3. Duration & Period Comparison Insights
        val durationComp = comparisons.firstOrNull { it.metricName == "Average Sleep Duration" }
        if (durationComp != null && durationComp.isComparisonReliable && durationComp.absoluteDifference != null) {
            val diff = durationComp.absoluteDifference
            val hours = (metrics.averageDurationMinutes ?: 0.0) / 60.0
            val formattedHours = String.format(java.util.Locale.US, "%.1f", hours)

            if (diff >= 20.0) {
                insights.add(
                    SleepInsight(
                        category = InsightCategory.DURATION,
                        title = "Longer Average Sleep Duration",
                        observation = "Average recorded sleep was ${diff.toInt()} minutes longer than in the preceding comparison period.",
                        explanation = "Current period averaged $formattedHours hours per night across ${durationComp.currentValidNights} recorded nights.",
                        evidenceLevel = EvidenceLevel.ROBUST,
                        comparisonPeriod = "Preceding period (${durationComp.previousValidNights} nights)"
                    )
                )
            } else if (diff <= -20.0) {
                insights.add(
                    SleepInsight(
                        category = InsightCategory.DURATION,
                        title = "Shorter Average Sleep Duration",
                        observation = "Average recorded sleep was ${kotlin.math.abs(diff.toInt())} minutes shorter than in the preceding comparison period.",
                        explanation = "Current period averaged $formattedHours hours per night across ${durationComp.currentValidNights} recorded nights.",
                        evidenceLevel = EvidenceLevel.ROBUST,
                        comparisonPeriod = "Preceding period (${durationComp.previousValidNights} nights)"
                    )
                )
            }
        }

        // 4. Sleep Onset Latency (SOL) Insights
        val avgSol = metrics.averageSolMinutes
        if (avgSol != null) {
            insights.add(
                SleepInsight(
                    category = InsightCategory.LATENCY,
                    title = "Recorded Sleep Latency",
                    observation = "Average time to fall asleep was ${avgSol.toInt()} minutes.",
                    explanation = "Derived from self-reported or explicit onset timestamps. Consumer wearables approximate this via motion stillness.",
                    evidenceLevel = EvidenceLevel.MODERATE
                )
            )
        }

        // 5. PSQI Monthly Context
        if (latestPsqiScore != null) {
            val interpretation = if (latestPsqiScore <= 5) "good subjective sleep quality" else "moderate sleep disturbance"
            insights.add(
                SleepInsight(
                    category = InsightCategory.DATA_COVERAGE,
                    title = "Monthly PSQI Context",
                    observation = "Latest monthly PSQI global score: $latestPsqiScore / 21 ($interpretation).",
                    explanation = "The PSQI is a validated monthly self-report screening instrument covering the past month, complementing your nightly recorded data.",
                    evidenceLevel = EvidenceLevel.ROBUST
                )
            )
        }

        return insights
    }
}
