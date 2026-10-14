package com.example.somnus.domain.dashboard

import com.example.somnus.data.healthconnect.HealthConnectEnergyExpenditure
import com.example.somnus.domain.analytics.DaySleepSummary
import com.example.somnus.domain.model.DailyNutritionSummary
import com.example.somnus.domain.model.DailyStepSummary
import com.example.somnus.domain.model.StepSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class CrossMetricInsightEngineTest {

    private val baseDate = LocalDate.of(2026, 10, 1)

    @Test
    fun activityVsSleepDuration_insufficientData_returnsInsufficientInsight() {
        val sleepSummaries = listOf(
            createSleepSummary(baseDate, durationMinutes = 450),
            createSleepSummary(baseDate.plusDays(1), durationMinutes = 480)
        )
        val stepSummaries = listOf(
            createStepSummary(baseDate, steps = 8000),
            createStepSummary(baseDate.plusDays(1), steps = 10000)
        )

        val insight = CrossMetricInsightEngine.evaluateActivityVsSleepDuration(sleepSummaries, stepSummaries)

        assertNotNull(insight)
        assertEquals(InsightConfidence.INSUFFICIENT, insight?.confidence)
        assertFalse(insight?.isStatisticallyReliable ?: true)
        assertEquals(2, insight?.validDaysCount)
        assertEquals(CrossMetricInsightEngine.MINIMUM_PAIRED_DAYS, insight?.minimumDaysRequired)
        assertTrue(insight?.headline?.contains("Collecting paired data (2 / 3 days)") == true)
    }

    @Test
    fun activityVsSleepDuration_longerSleepOnActiveDays_detectsPositiveAssociation() {
        // 5 paired days -> Robust (SUFFICIENT_DATA)
        // High activity days (steps >= median): longer sleep
        // Low activity days (steps < median): shorter sleep
        val dates = (0 until 5).map { baseDate.plusDays(it.toLong()) }
        val steps = listOf(4000L, 5000L, 8000L, 10000L, 12000L) // median = 8000
        val sleep = listOf(400L, 410L, 480L, 500L, 510L) // high avg = 496.6m, low avg = 405m -> diff = +92m

        val sleepSummaries = dates.mapIndexed { i, d -> createSleepSummary(d, durationMinutes = sleep[i]) }
        val stepSummaries = dates.mapIndexed { i, d -> createStepSummary(d, steps = steps[i]) }

        val insight = CrossMetricInsightEngine.evaluateActivityVsSleepDuration(sleepSummaries, stepSummaries)

        assertNotNull(insight)
        assertEquals(InsightConfidence.SUFFICIENT_DATA, insight?.confidence)
        assertTrue(insight?.isStatisticallyReliable == true)
        assertTrue(insight?.headline?.contains("Longer sleep recorded on higher-activity days") == true)
        assertTrue(insight?.caveat?.contains("does not imply causation") == true)
    }

    @Test
    fun activityVsSleepDuration_shorterSleepOnActiveDays_detectsNegativeAssociation() {
        val dates = (0 until 5).map { baseDate.plusDays(it.toLong()) }
        val steps = listOf(3000L, 4000L, 7000L, 11000L, 13000L) // median = 7000
        val sleep = listOf(500L, 490L, 420L, 400L, 390L) // high avg = 403m, low avg = 495m -> diff = -92m

        val sleepSummaries = dates.mapIndexed { i, d -> createSleepSummary(d, durationMinutes = sleep[i]) }
        val stepSummaries = dates.mapIndexed { i, d -> createStepSummary(d, steps = steps[i]) }

        val insight = CrossMetricInsightEngine.evaluateActivityVsSleepDuration(sleepSummaries, stepSummaries)

        assertNotNull(insight)
        assertTrue(insight?.headline?.contains("Shorter sleep recorded on higher-activity days") == true)
    }

    @Test
    fun activityVsSleepDuration_consistentSleepAcrossActivity_detectsConsistency() {
        val dates = (0 until 4).map { baseDate.plusDays(it.toLong()) } // 4 days -> PRELIMINARY
        val steps = listOf(4000L, 6000L, 9000L, 11000L)
        val sleep = listOf(450L, 455L, 452L, 458L) // diff < 15 mins

        val sleepSummaries = dates.mapIndexed { i, d -> createSleepSummary(d, durationMinutes = sleep[i]) }
        val stepSummaries = dates.mapIndexed { i, d -> createStepSummary(d, steps = steps[i]) }

        val insight = CrossMetricInsightEngine.evaluateActivityVsSleepDuration(sleepSummaries, stepSummaries)

        assertNotNull(insight)
        assertEquals(InsightConfidence.PRELIMINARY, insight?.confidence)
        assertFalse(insight?.isStatisticallyReliable ?: true)
        assertTrue(insight?.headline?.contains("Consistent sleep duration across activity levels") == true)
    }

    @Test
    fun activityVsBedtime_insufficientDays_returnsNull() {
        val sleepSummaries = listOf(
            createSleepSummary(baseDate, bedtime = LocalTime.of(22, 30)),
            createSleepSummary(baseDate.plusDays(1), bedtime = LocalTime.of(23, 0))
        )
        val stepSummaries = listOf(
            createStepSummary(baseDate, steps = 5000),
            createStepSummary(baseDate.plusDays(1), steps = 9000)
        )

        val insight = CrossMetricInsightEngine.evaluateActivityVsBedtime(sleepSummaries, stepSummaries)
        assertNull(insight)
    }

    @Test
    fun activityVsBedtime_earlierBedtimeOnActiveDays_detectsEarlierSchedule() {
        val dates = (0 until 4).map { baseDate.plusDays(it.toLong()) }
        val steps = listOf(3000L, 4000L, 8000L, 10000L) // median = 8000
        // Lower activity bedtime: 23:30, 23:45 (~23:37)
        // Higher activity bedtime: 22:15, 22:30 (~22:22) -> ~75 minutes earlier
        val bedtimes = listOf(
            LocalTime.of(23, 30),
            LocalTime.of(23, 45),
            LocalTime.of(22, 15),
            LocalTime.of(22, 30)
        )

        val sleepSummaries = dates.mapIndexed { i, d -> createSleepSummary(d, bedtime = bedtimes[i]) }
        val stepSummaries = dates.mapIndexed { i, d -> createStepSummary(d, steps = steps[i]) }

        val insight = CrossMetricInsightEngine.evaluateActivityVsBedtime(sleepSummaries, stepSummaries)

        assertNotNull(insight)
        assertTrue(insight?.headline?.contains("Earlier bedtime on higher-activity days") == true)
        assertEquals(CrossMetricType.ACTIVITY_VS_BEDTIME, insight?.type)
    }

    @Test
    fun evaluateEnergyBalance_validData_returnsComparisonWithCaveats() {
        val dates = listOf(baseDate, baseDate.plusDays(1), baseDate.plusDays(2))
        val nutritionSummaries = dates.map {
            DailyNutritionSummary(
                date = it,
                totalCalories = 2100.0,
                totalProtein = 80.0,
                totalCarbs = 250.0,
                totalFat = 60.0,
                calorieTarget = 2000,
                entries = emptyList()
            )
        }
        val energyMap = dates.associateWith {
            HealthConnectEnergyExpenditure(
                activeCaloriesKcal = 450.0,
                totalCaloriesKcal = 2200.0
            )
        }

        val insight = CrossMetricInsightEngine.evaluateEnergyBalance(nutritionSummaries, energyMap)

        assertNotNull(insight)
        assertEquals(CrossMetricType.ENERGY_BALANCE, insight?.type)
        assertEquals(InsightConfidence.SUFFICIENT_DATA, insight?.confidence)
        assertTrue(insight?.headline?.contains("2100 kcal intake vs. 450 kcal active burn") == true)
        assertTrue(insight?.explanation?.contains("total recorded burn: 2200 kcal") == true)
        assertEquals("Analytical comparison only — not a clinical metabolic measurement.", insight?.caveat)
    }

    @Test
    fun evaluateMultiMetricTrend_comparesCurrent7DaysVsPrior7Days() {
        val refDate = LocalDate.of(2026, 10, 14)
        // Need at least 3 sleep & 3 step days in current period (Oct 8..14)
        // and at least 3 sleep & 3 step days in prior period (Oct 1..7)
        val curDates = (0 until 4).map { refDate.minusDays(it.toLong()) }
        val prevDates = (7 until 11).map { refDate.minusDays(it.toLong()) }

        val allSleep = curDates.map { createSleepSummary(it, durationMinutes = 480) } +
                prevDates.map { createSleepSummary(it, durationMinutes = 420) }

        val allSteps = curDates.map { createStepSummary(it, steps = 10000) } +
                prevDates.map { createStepSummary(it, steps = 6000) }

        val insight = CrossMetricInsightEngine.evaluateMultiMetricTrend(allSleep, allSteps, referenceDate = refDate)

        assertNotNull(insight)
        assertEquals(CrossMetricType.MULTI_DAY_TREND, insight?.type)
        assertTrue(insight?.headline?.contains("Sleep +60m/night") == true)
        assertTrue(insight?.headline?.contains("Steps +4,000/day") == true)
        assertTrue(insight?.isStatisticallyReliable == true)
    }

    @Test
    fun evaluateMultiMetricTrend_insufficientPriorDays_returnsNull() {
        val refDate = LocalDate.of(2026, 10, 14)
        val curDates = (0 until 4).map { refDate.minusDays(it.toLong()) }
        // Only 1 day in prior period
        val prevDates = listOf(refDate.minusDays(8))

        val allSleep = curDates.map { createSleepSummary(it, durationMinutes = 480) } +
                prevDates.map { createSleepSummary(it, durationMinutes = 420) }

        val allSteps = curDates.map { createStepSummary(it, steps = 10000) } +
                prevDates.map { createStepSummary(it, steps = 6000) }

        val insight = CrossMetricInsightEngine.evaluateMultiMetricTrend(allSleep, allSteps, referenceDate = refDate)
        assertNull(insight)
    }

    private fun createSleepSummary(
        date: LocalDate,
        durationMinutes: Long = 480,
        bedtime: LocalTime? = LocalTime.of(23, 0)
    ): DaySleepSummary {
        return DaySleepSummary(
            sleepDate = date,
            totalSleepDurationMinutes = durationMinutes,
            totalTimeInBedMinutes = durationMinutes,
            sessionCount = 1,
            bedtime = bedtime,
            wakeTime = LocalTime.of(7, 0),
            sources = setOf(com.example.somnus.domain.model.SessionSource.PHONE_ESTIMATE)
        )
    }

    private fun createStepSummary(
        date: LocalDate,
        steps: Long = 8000
    ): DailyStepSummary {
        return DailyStepSummary(
            date = date,
            totalSteps = steps,
            goalSteps = 8000,
            sourcesPresent = setOf(StepSource.PHONE_HARDWARE)
        )
    }
}
