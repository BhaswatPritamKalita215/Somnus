package com.example.somnus.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class IntentRouterTest {

    private val today = LocalDate.of(2026, 10, 14)

    @Test
    fun route_todayStepsQuery_routesToTodaySteps() {
        val result = IntentRouter.route("How many steps did I take today?", today)
        assertEquals(IntentType.TODAY_STEPS, result.intentType)
        assertTrue(result.isLocal)
        assertEquals(today, result.targetDate)
    }

    @Test
    fun route_yesterdayStepsQuery_routesToHistoricalSteps() {
        val result = IntentRouter.route("How many steps yesterday?", today)
        assertEquals(IntentType.HISTORICAL_STEPS, result.intentType)
        assertTrue(result.isLocal)
        assertEquals(today.minusDays(1), result.targetDate)
    }

    @Test
    fun route_latestSleepQuery_routesToLatestSleep() {
        val result = IntentRouter.route("How did I sleep last night?", today)
        assertEquals(IntentType.LATEST_SLEEP, result.intentType)
        assertTrue(result.isLocal)
        assertEquals(today, result.targetDate)
    }

    @Test
    fun route_todayCaloriesQuery_routesToTodayNutrition() {
        val result = IntentRouter.route("What is my calorie intake today?", today)
        assertEquals(IntentType.TODAY_NUTRITION, result.intentType)
        assertTrue(result.isLocal)
    }

    @Test
    fun route_macronutrientsQuery_routesToTodayNutrition() {
        val result = IntentRouter.route("Show my protein and carb macros for today", today)
        assertEquals(IntentType.TODAY_NUTRITION, result.intentType)
        assertTrue(result.isLocal)
    }

    @Test
    fun route_energyExpenditureQuery_routesToEnergyExpenditure() {
        val result = IntentRouter.route("How many calories did I burn today?", today)
        assertEquals(IntentType.ENERGY_EXPENDITURE, result.intentType)
        assertTrue(result.isLocal)
    }

    @Test
    fun route_crossMetricSummaryQuery_routesToCrossMetricCorrelation() {
        val result = IntentRouter.route("What is the relationship between my steps vs sleep?", today)
        assertEquals(IntentType.CROSS_METRIC_CORRELATION, result.intentType)
        assertTrue(result.isLocal)
    }

    @Test
    fun route_dataGapsQuery_routesToMissingDataExplanation() {
        val result = IntentRouter.route("Why is my sleep data missing?", today)
        assertEquals(IntentType.MISSING_DATA_EXPLANATION, result.intentType)
        assertTrue(result.isLocal)
    }

    @Test
    fun route_greetingQuery_routesToGreetingOrHelp() {
        val result = IntentRouter.route("Hello, what can you do?", today)
        assertEquals(IntentType.GREETING_OR_HELP, result.intentType)
        assertTrue(result.isLocal)
        assertNotNull(result.directResponse)
    }

    @Test
    fun route_medicalDiagnosisQuery_routesToOutOfScopeMedical() {
        val result = IntentRouter.route("Do I have chronic insomnia or sleep apnea?", today)
        assertEquals(IntentType.OUT_OF_SCOPE_MEDICAL, result.intentType)
        assertTrue(result.isLocal)
        assertNotNull(result.directResponse)
        assertTrue(result.directResponse?.contains("cannot diagnose") == true)
    }

    @Test
    fun route_medicationAdviceQuery_routesToOutOfScopeMedical() {
        val result = IntentRouter.route("What dosage of sleeping pill or melatonin dose should I take?", today)
        assertEquals(IntentType.OUT_OF_SCOPE_MEDICAL, result.intentType)
        assertTrue(result.isLocal)
    }

    @Test
    fun route_complexCorrelationQuery_routesToModelSynthesis() {
        val result = IntentRouter.route("Why do I wake up feeling unrefreshed in the morning?", today)
        assertEquals(IntentType.COMPLEX_SYNTHESIS, result.intentType)
        assertFalse(result.isLocal)
    }

    @Test
    fun route_rangeQuery_extractsDateRange() {
        val result = IntentRouter.route("How many steps did I take over the past 7 days?", today)
        assertEquals(IntentType.HISTORICAL_STEPS, result.intentType)
        assertNotNull(result.dateRange)
        assertEquals(today.minusDays(6), result.dateRange?.start)
        assertEquals(today, result.dateRange?.endInclusive)
    }
}
