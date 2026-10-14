package com.example.somnus.data.sensor

import com.example.somnus.domain.model.StepCounterCheckpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PhoneStepTrackerTest {

    private val testDate = LocalDate.of(2026, 10, 14)

    @Test
    fun calculateStepCounterDelta_whenInitialBaseline_setsCheckpointWithZeroDelta() {
        val result = PhoneStepTracker.calculateStepCounterDelta(
            currentSensorValue = 5420.0f,
            existingCheckpoint = null,
            nowEpochMs = 1700000000000L,
            currentLocalDate = testDate,
            currentBootTimestamp = 120000L
        )

        assertEquals(0L, result.deltaSteps)
        assertEquals(5420.0f, result.newCheckpoint.lastSensorValue, 0.01f)
        assertEquals(0L, result.newCheckpoint.accumulatedSteps)
        assertFalse(result.isMidnightRollover)
        assertFalse(result.isRebootOrReset)
    }

    @Test
    fun calculateStepCounterDelta_normalIncrement_calculatesExactDeltaAndAccumulates() {
        val existingCheckpoint = StepCounterCheckpoint(
            bootTimestamp = 120000L,
            lastSensorValue = 5420.0f,
            accumulatedSteps = 100L,
            lastUpdateEpochMs = 1700000000000L,
            localDate = testDate.toString()
        )

        val result = PhoneStepTracker.calculateStepCounterDelta(
            currentSensorValue = 5720.0f, // 300 steps taken
            existingCheckpoint = existingCheckpoint,
            nowEpochMs = 1700000300000L,
            currentLocalDate = testDate,
            currentBootTimestamp = 420000L
        )

        assertEquals(300L, result.deltaSteps)
        assertEquals(5720.0f, result.newCheckpoint.lastSensorValue, 0.01f)
        assertEquals(400L, result.newCheckpoint.accumulatedSteps) // 100 + 300
        assertFalse(result.isMidnightRollover)
        assertFalse(result.isRebootOrReset)
    }

    @Test
    fun calculateStepCounterDelta_deviceRebootOrReset_detectsResetAndPreventsNegativeDelta() {
        val existingCheckpoint = StepCounterCheckpoint(
            bootTimestamp = 120000L,
            lastSensorValue = 15000.0f, // Before reboot was 15,000
            accumulatedSteps = 8000L,
            lastUpdateEpochMs = 1700000000000L,
            localDate = testDate.toString()
        )

        // Device rebooted! Hardware step counter reset to 0 and now reads 50
        val result = PhoneStepTracker.calculateStepCounterDelta(
            currentSensorValue = 50.0f,
            existingCheckpoint = existingCheckpoint,
            nowEpochMs = 1700003600000L,
            currentLocalDate = testDate,
            currentBootTimestamp = 30000L // New boot time
        )

        assertTrue(result.isRebootOrReset)
        // Delta must be 50, NEVER negative (50 - 15000 = -14950)
        assertEquals(50L, result.deltaSteps)
        assertEquals(50.0f, result.newCheckpoint.lastSensorValue, 0.01f)
        assertEquals(8050L, result.newCheckpoint.accumulatedSteps) // Preserves day's previous 8000 + 50
    }

    @Test
    fun calculateStepCounterDelta_midnightRollover_resetsAccumulatedStepsForNewDay() {
        val yesterday = LocalDate.of(2026, 10, 13)
        val today = LocalDate.of(2026, 10, 14)

        val yesterdayCheckpoint = StepCounterCheckpoint(
            bootTimestamp = 120000L,
            lastSensorValue = 8000.0f,
            accumulatedSteps = 8000L,
            lastUpdateEpochMs = 1700000000000L,
            localDate = yesterday.toString()
        )

        // Sensor now reads 8100 on the new day
        val result = PhoneStepTracker.calculateStepCounterDelta(
            currentSensorValue = 8100.0f,
            existingCheckpoint = yesterdayCheckpoint,
            nowEpochMs = 1700003600000L,
            currentLocalDate = today,
            currentBootTimestamp = 480000L
        )

        assertTrue(result.isMidnightRollover)
        assertEquals(100L, result.deltaSteps)
        assertEquals(100L, result.newCheckpoint.accumulatedSteps) // New day starts fresh with 100 steps
        assertEquals(today.toString(), result.newCheckpoint.localDate)
    }

    @Test
    fun calculateStepCounterDelta_noStepChange_returnsZeroDelta() {
        val existingCheckpoint = StepCounterCheckpoint(
            bootTimestamp = 120000L,
            lastSensorValue = 5000.0f,
            accumulatedSteps = 1200L,
            lastUpdateEpochMs = 1700000000000L,
            localDate = testDate.toString()
        )

        val result = PhoneStepTracker.calculateStepCounterDelta(
            currentSensorValue = 5000.0f,
            existingCheckpoint = existingCheckpoint,
            nowEpochMs = 1700000060000L,
            currentLocalDate = testDate,
            currentBootTimestamp = 180000L
        )

        assertEquals(0L, result.deltaSteps)
        assertEquals(1200L, result.newCheckpoint.accumulatedSteps)
        assertFalse(result.isRebootOrReset)
        assertFalse(result.isMidnightRollover)
    }
}
