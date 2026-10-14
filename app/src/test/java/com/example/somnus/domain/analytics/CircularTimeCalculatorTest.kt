package com.example.somnus.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class CircularTimeCalculatorTest {

    @Test
    fun `empty list returns null circular mean and null stdDev`() {
        val mean = CircularTimeCalculator.calculateCircularMean(emptyList())
        val stdDev = CircularTimeCalculator.calculateCircularStandardDeviationMinutes(emptyList())

        assertNull(mean)
        assertNull(stdDev)
    }

    @Test
    fun `single time returns exact time and null stdDev`() {
        val time = LocalTime.of(23, 15)
        val mean = CircularTimeCalculator.calculateCircularMean(listOf(time))
        val stdDev = CircularTimeCalculator.calculateCircularStandardDeviationMinutes(listOf(time))

        assertNotNull(mean)
        assertEquals(LocalTime.of(23, 15), mean)
        assertNull(stdDev)
    }

    @Test
    fun `identical times return identical mean and zero stdDev`() {
        val times = List(5) { LocalTime.of(22, 30) }
        val mean = CircularTimeCalculator.calculateCircularMean(times)
        val stdDev = CircularTimeCalculator.calculateCircularStandardDeviationMinutes(times)

        assertNotNull(mean)
        assertEquals(LocalTime.of(22, 30), mean)
        assertEquals(0.0, stdDev!!, 0.001)
    }

    @Test
    fun `critical midnight crossing average is correctly midnight not noon`() {
        // 23:30 (half an hour before midnight) and 00:30 (half an hour after midnight)
        // Standard arithmetic average would compute (23.5 + 0.5) / 2 = 12:00 (NOON)!
        // Circular statistics MUST yield 00:00 (MIDNIGHT)!
        val times = listOf(
            LocalTime.of(23, 30),
            LocalTime.of(0, 30)
        )
        val mean = CircularTimeCalculator.calculateCircularMean(times)
        val stdDev = CircularTimeCalculator.calculateCircularStandardDeviationMinutes(times)

        assertNotNull(mean)
        assertEquals(LocalTime.of(0, 0), mean)
        // Standard deviation: about 42 minutes for 1-hour separation
        assertTrue(stdDev != null && stdDev > 0.0)
    }

    @Test
    fun `three night midnight straddling yields exact midnight`() {
        val times = listOf(
            LocalTime.of(23, 0),
            LocalTime.of(0, 0),
            LocalTime.of(1, 0)
        )
        val mean = CircularTimeCalculator.calculateCircularMean(times)
        val stdDev = CircularTimeCalculator.calculateCircularStandardDeviationMinutes(times)

        assertNotNull(mean)
        assertEquals(LocalTime.of(0, 0), mean)
        assertTrue(stdDev != null && stdDev > 30.0 && stdDev < 60.0)
    }

    @Test
    fun `variability distinguishes consistent from scattered bedtimes`() {
        // High consistency (all within 10 minutes)
        val consistentTimes = listOf(
            LocalTime.of(23, 10),
            LocalTime.of(23, 15),
            LocalTime.of(23, 20),
            LocalTime.of(23, 12)
        )
        val consistentDev = CircularTimeCalculator.calculateCircularStandardDeviationMinutes(consistentTimes)
        assertNotNull(consistentDev)
        assertTrue(consistentDev!! < 10.0)

        // Scattered bedtimes
        val scatteredTimes = listOf(
            LocalTime.of(21, 0),
            LocalTime.of(23, 30),
            LocalTime.of(1, 30),
            LocalTime.of(3, 0)
        )
        val scatteredDev = CircularTimeCalculator.calculateCircularStandardDeviationMinutes(scatteredTimes)
        assertNotNull(scatteredDev)
        assertTrue(scatteredDev!! > 60.0)
    }
}
