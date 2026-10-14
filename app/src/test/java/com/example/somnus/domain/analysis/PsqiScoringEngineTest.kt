package com.example.somnus.domain.analysis

import com.example.somnus.domain.model.PsqiResponses
import com.example.somnus.domain.model.SyntheticPsqiFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PsqiScoringEngineTest {

    @Test
    fun scoreComponent1_subjectiveSleepQuality_scoresCorrectly() {
        assertEquals(0, PsqiScoringEngine.scoreComponent1(0))
        assertEquals(1, PsqiScoringEngine.scoreComponent1(1))
        assertEquals(2, PsqiScoringEngine.scoreComponent1(2))
        assertEquals(3, PsqiScoringEngine.scoreComponent1(3))
    }

    @Test
    fun scoreComponent2_sleepLatency_combinesDurationAndFrequencyThresholds() {
        // Q2 <= 15m (0) + Q5a (0) = 0 -> C2 = 0
        assertEquals(0, PsqiScoringEngine.scoreComponent2(10, 0))
        assertEquals(0, PsqiScoringEngine.scoreComponent2(15, 0))

        // Q2 16-30m (1) + Q5a (0) = 1 -> C2 = 1
        assertEquals(1, PsqiScoringEngine.scoreComponent2(20, 0))
        // Q2 <= 15m (0) + Q5a (2) = 2 -> C2 = 1
        assertEquals(1, PsqiScoringEngine.scoreComponent2(15, 2))

        // Q2 31-60m (2) + Q5a (1) = 3 -> C2 = 2
        assertEquals(2, PsqiScoringEngine.scoreComponent2(45, 1))
        // Q2 16-30m (1) + Q5a (3) = 4 -> C2 = 2
        assertEquals(2, PsqiScoringEngine.scoreComponent2(25, 3))

        // Q2 > 60m (3) + Q5a (2) = 5 -> C2 = 3
        assertEquals(3, PsqiScoringEngine.scoreComponent2(75, 2))
        // Q2 > 60m (3) + Q5a (3) = 6 -> C2 = 3
        assertEquals(3, PsqiScoringEngine.scoreComponent2(90, 3))
    }

    @Test
    fun scoreComponent3_sleepDuration_usesExactCutoffs() {
        // > 7 hours = 0
        assertEquals(0, PsqiScoringEngine.scoreComponent3(7.5))
        assertEquals(0, PsqiScoringEngine.scoreComponent3(7.1))

        // 6-7 hours = 1 (inclusive 6.0 and 7.0)
        assertEquals(1, PsqiScoringEngine.scoreComponent3(7.0))
        assertEquals(1, PsqiScoringEngine.scoreComponent3(6.5))
        assertEquals(1, PsqiScoringEngine.scoreComponent3(6.0))

        // 5-6 hours = 2
        assertEquals(2, PsqiScoringEngine.scoreComponent3(5.9))
        assertEquals(2, PsqiScoringEngine.scoreComponent3(5.0))

        // < 5 hours = 3
        assertEquals(3, PsqiScoringEngine.scoreComponent3(4.9))
        assertEquals(3, PsqiScoringEngine.scoreComponent3(3.0))
    }

    @Test
    fun scoreComponent4_sleepEfficiency_calculatesPercentageAndAppliesClinicalThresholds() {
        // >= 85% = 0
        assertEquals(0, PsqiScoringEngine.scoreComponent4(95.0))
        assertEquals(0, PsqiScoringEngine.scoreComponent4(85.0))

        // 75-84.9% = 1
        assertEquals(1, PsqiScoringEngine.scoreComponent4(84.9))
        assertEquals(1, PsqiScoringEngine.scoreComponent4(75.0))

        // 65-74.9% = 2
        assertEquals(2, PsqiScoringEngine.scoreComponent4(74.9))
        assertEquals(2, PsqiScoringEngine.scoreComponent4(65.0))

        // < 65% = 3
        assertEquals(3, PsqiScoringEngine.scoreComponent4(64.9))
        assertEquals(3, PsqiScoringEngine.scoreComponent4(40.0))
    }

    @Test
    fun calculateHoursInBed_crossesMidnight_correctDuration() {
        val bedTime = "23:00"
        val wakeTime = "07:00"
        val hours = PsqiScoringEngine.calculateHoursInBed(bedTime, wakeTime)
        assertEquals(8.0, hours, 0.01)

        val bedLate = "01:30"
        val wakeLate = "09:00"
        val hoursLate = PsqiScoringEngine.calculateHoursInBed(bedLate, wakeLate)
        assertEquals(7.5, hoursLate, 0.01)
    }

    @Test
    fun scoreComponent5_sleepDisturbances_sums9Items() {
        // All 0s -> sum = 0 -> 0
        assertEquals(0, PsqiScoringEngine.scoreComponent5(List(9) { 0 }))

        // Sum 1 to 9 -> 1
        assertEquals(1, PsqiScoringEngine.scoreComponent5(listOf(1, 0, 0, 0, 0, 0, 0, 0, 0)))
        assertEquals(1, PsqiScoringEngine.scoreComponent5(listOf(1, 1, 1, 1, 1, 1, 1, 1, 1))) // sum = 9

        // Sum 10 to 18 -> 2
        assertEquals(2, PsqiScoringEngine.scoreComponent5(listOf(2, 1, 1, 1, 1, 1, 1, 1, 1))) // sum = 10
        assertEquals(2, PsqiScoringEngine.scoreComponent5(List(9) { 2 })) // sum = 18

        // Sum 19 to 27 -> 3
        assertEquals(3, PsqiScoringEngine.scoreComponent5(listOf(3, 2, 2, 2, 2, 2, 2, 2, 2))) // sum = 19
        assertEquals(3, PsqiScoringEngine.scoreComponent5(List(9) { 3 })) // sum = 27
    }

    @Test
    fun scoreComponent7_daytimeDysfunction_combinesQ7AndQ8() {
        // Sum 0 -> 0
        assertEquals(0, PsqiScoringEngine.scoreComponent7(0, 0))

        // Sum 1-2 -> 1
        assertEquals(1, PsqiScoringEngine.scoreComponent7(1, 0))
        assertEquals(1, PsqiScoringEngine.scoreComponent7(1, 1))

        // Sum 3-4 -> 2
        assertEquals(2, PsqiScoringEngine.scoreComponent7(2, 1))
        assertEquals(2, PsqiScoringEngine.scoreComponent7(2, 2))

        // Sum 5-6 -> 3
        assertEquals(3, PsqiScoringEngine.scoreComponent7(3, 2))
        assertEquals(3, PsqiScoringEngine.scoreComponent7(3, 3))
    }

    @Test
    fun score_perfectSleepFixture_producesGlobalScoreZero() {
        val result = PsqiScoringEngine.score(SyntheticPsqiFixtures.PERFECT_SLEEP)

        assertTrue(result is PsqiScoringResult.Complete)
        val complete = result as PsqiScoringResult.Complete

        assertEquals(0, complete.globalScore)
        assertEquals(0, complete.componentScores.c1SubjectiveQuality)
        assertEquals(0, complete.componentScores.c2SleepLatency)
        assertEquals(0, complete.componentScores.c3SleepDuration)
        assertEquals(0, complete.componentScores.c4SleepEfficiency)
        assertEquals(0, complete.componentScores.c5SleepDisturbance)
        assertEquals(0, complete.componentScores.c6UseOfMedication)
        assertEquals(0, complete.componentScores.c7DaytimeDysfunction)
        assertFalse(complete.componentScores.isDisturbedSleep)
    }

    @Test
    fun score_maximalSeverityFixture_producesGlobalScore21() {
        val result = PsqiScoringEngine.score(SyntheticPsqiFixtures.MAXIMAL_SEVERITY)

        assertTrue(result is PsqiScoringResult.Complete)
        val complete = result as PsqiScoringResult.Complete

        assertEquals(21, complete.globalScore)
        assertEquals(3, complete.componentScores.c1SubjectiveQuality)
        assertEquals(3, complete.componentScores.c2SleepLatency)
        assertEquals(3, complete.componentScores.c3SleepDuration)
        assertEquals(3, complete.componentScores.c4SleepEfficiency)
        assertEquals(3, complete.componentScores.c5SleepDisturbance)
        assertEquals(3, complete.componentScores.c6UseOfMedication)
        assertEquals(3, complete.componentScores.c7DaytimeDysfunction)
        assertTrue(complete.componentScores.isDisturbedSleep)
    }

    @Test
    fun score_moderateDisturbanceFixture_producesGlobalScore8() {
        val result = PsqiScoringEngine.score(SyntheticPsqiFixtures.MODERATE_DISTURBANCE)

        assertTrue(result is PsqiScoringResult.Complete)
        val complete = result as PsqiScoringResult.Complete

        assertEquals(8, complete.globalScore)
        assertEquals(2, complete.componentScores.c1SubjectiveQuality)
        assertEquals(1, complete.componentScores.c2SleepLatency)
        assertEquals(1, complete.componentScores.c3SleepDuration)
        assertEquals(1, complete.componentScores.c4SleepEfficiency)
        assertEquals(1, complete.componentScores.c5SleepDisturbance)
        assertEquals(1, complete.componentScores.c6UseOfMedication)
        assertEquals(1, complete.componentScores.c7DaytimeDysfunction)
        assertTrue(complete.componentScores.isDisturbedSleep) // > 5
    }

    @Test
    fun score_incompleteFixture_neverConvertsToZeroAndReturnsIncompleteResult() {
        val result = PsqiScoringEngine.score(SyntheticPsqiFixtures.INCOMPLETE_FIXTURE)

        assertTrue(result is PsqiScoringResult.Incomplete)
        val incomplete = result as PsqiScoringResult.Incomplete

        assertTrue(incomplete.missingItems.isNotEmpty())
        assertTrue(incomplete.missingItems.any { it.contains("Item 5b") })
        assertTrue(incomplete.missingItems.any { it.contains("Item 6") })
        assertTrue(incomplete.missingItems.any { it.contains("Item 9") })
    }
}
