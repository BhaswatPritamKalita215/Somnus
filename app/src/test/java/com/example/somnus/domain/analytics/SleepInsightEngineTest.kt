package com.example.somnus.domain.analytics

import com.example.somnus.domain.model.SessionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class SleepInsightEngineTest {

    private fun createMetrics(
        nightsTracked: Int,
        totalDays: Int = 7,
        avgDuration: Double = 480.0,
        bedtimeVar: Double? = 15.0,
        avgSol: Double? = 15.0
    ): PeriodSleepMetrics {
        return PeriodSleepMetrics(
            averageDurationMinutes = avgDuration,
            averageTibMinutes = avgDuration + 20.0,
            typicalBedtime = LocalTime.of(23, 0),
            typicalWakeTime = LocalTime.of(7, 0),
            bedtimeVariabilityMinutes = bedtimeVar,
            wakeTimeVariabilityMinutes = 15.0,
            durationVariabilityMinutes = 20.0,
            averageSolMinutes = avgSol,
            totalNightsTracked = nightsTracked,
            totalDaysInRange = totalDays,
            coveragePercentage = (nightsTracked.toDouble() / totalDays) * 100.0
        )
    }

    private fun createQuality(
        level: DataQualityLevel = DataQualityLevel.ADEQUATE,
        nightsTracked: Int = 5,
        totalDays: Int = 7
    ): SleepDataQuality {
        return SleepDataQuality(
            totalDays = totalDays,
            nightsWithUsableData = nightsTracked,
            missingNightsCount = totalDays - nightsTracked,
            conflictedNightsCount = 0,
            sourceBreakdown = mapOf(SessionSource.MANUAL to nightsTracked),
            coveragePercentage = (nightsTracked.toDouble() / totalDays) * 100.0,
            qualityLevel = level
        )
    }

    @Test
    fun `insufficient data generates baseline building insight and suppresses other insights`() {
        val metrics = createMetrics(nightsTracked = 2) // Less than MIN_OBSERVATIONS_FOR_INSIGHTS (3)
        val quality = createQuality(level = DataQualityLevel.INSUFFICIENT, nightsTracked = 2)

        val insights = SleepInsightEngine.generateInsights(
            metrics = metrics,
            comparisons = emptyList(),
            dataQuality = quality,
            rangeLabel = "Last 7 Days"
        )

        assertEquals(1, insights.size)
        val insight = insights.first()
        assertEquals(InsightCategory.DATA_COVERAGE, insight.category)
        assertTrue(insight.title.contains("Building Your Baseline"))
        assertEquals(EvidenceLevel.PRELIMINARY, insight.evidenceLevel)
        // Verify non-diagnostic claim
        assertFalse(insight.explanation.contains("disorder"))
        assertFalse(insight.explanation.contains("insomnia"))
    }

    @Test
    fun `consistent bedtime triggers consistency insight with robust evidence`() {
        val metrics = createMetrics(
            nightsTracked = 6,
            bedtimeVar = 18.0 // <= 30 min
        )
        val quality = createQuality(level = DataQualityLevel.EXCELLENT, nightsTracked = 6)

        val insights = SleepInsightEngine.generateInsights(
            metrics = metrics,
            comparisons = emptyList(),
            dataQuality = quality,
            rangeLabel = "Last 7 Days"
        )

        val consistencyInsight = insights.find { it.category == InsightCategory.CONSISTENCY }
        assertTrue(consistencyInsight != null)
        assertTrue(consistencyInsight!!.title.contains("Consistent Bedtime Schedule"))
        assertEquals(EvidenceLevel.ROBUST, consistencyInsight.evidenceLevel)
    }

    @Test
    fun `variable bedtime triggers variable schedule insight`() {
        val metrics = createMetrics(
            nightsTracked = 6,
            bedtimeVar = 85.0 // >= 75 min
        )
        val quality = createQuality(level = DataQualityLevel.EXCELLENT, nightsTracked = 6)

        val insights = SleepInsightEngine.generateInsights(
            metrics = metrics,
            comparisons = emptyList(),
            dataQuality = quality,
            rangeLabel = "Last 7 Days"
        )

        val consistencyInsight = insights.find { it.category == InsightCategory.CONSISTENCY }
        assertTrue(consistencyInsight != null)
        assertTrue(consistencyInsight!!.title.contains("Variable Bedtime Schedule"))
    }

    @Test
    fun `sleep latency generates latency insight`() {
        val metrics = createMetrics(
            nightsTracked = 5,
            avgSol = 25.0
        )
        val quality = createQuality(level = DataQualityLevel.ADEQUATE, nightsTracked = 5)

        val insights = SleepInsightEngine.generateInsights(
            metrics = metrics,
            comparisons = emptyList(),
            dataQuality = quality,
            rangeLabel = "Last 7 Days"
        )

        val solInsight = insights.find { it.category == InsightCategory.LATENCY }
        assertTrue(solInsight != null)
        assertTrue(solInsight!!.title.contains("Recorded Sleep Latency"))
    }

    @Test
    fun `psqi context insight generated when score is provided`() {
        val metrics = createMetrics(nightsTracked = 5)
        val quality = createQuality(level = DataQualityLevel.ADEQUATE, nightsTracked = 5)

        val insights = SleepInsightEngine.generateInsights(
            metrics = metrics,
            comparisons = emptyList(),
            dataQuality = quality,
            rangeLabel = "Last 7 Days",
            latestPsqiScore = 4
        )

        val psqiInsight = insights.find { it.title.contains("Monthly PSQI Context") }
        assertTrue(psqiInsight != null)
        assertTrue(psqiInsight!!.observation.contains("good subjective sleep quality"))
    }
}
