package com.example.somnus.domain.analytics

import java.time.LocalTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure statistical calculation utility for circular wall-clock times.
 *
 * A conventional arithmetic average can produce completely misleading results
 * when sleep times cross midnight (e.g., averaging 23:30 and 00:30 linearly yields 12:00 noon,
 * whereas circular averaging yields 00:00 midnight).
 */
object CircularTimeCalculator {

    private const val MINUTES_PER_DAY = 1440.0
    private const val TWO_PI = 2.0 * Math.PI

    /**
     * Calculates the circular mean of a collection of [LocalTime] observations.
     *
     * @return The circular mean as [LocalTime], or null if the collection is empty.
     */
    fun calculateCircularMean(times: List<LocalTime>): LocalTime? {
        if (times.isEmpty()) return null
        if (times.size == 1) return times.first()

        var sumCos = 0.0
        var sumSin = 0.0

        for (time in times) {
            val minutes = time.hour * 60.0 + time.minute + (time.second / 60.0)
            val angle = (minutes / MINUTES_PER_DAY) * TWO_PI
            sumCos += cos(angle)
            sumSin += sin(angle)
        }

        val meanCos = sumCos / times.size
        val meanSin = sumSin / times.size

        // If all vectors cancel out completely (uniformly distributed around the clock),
        // fallback to the first time or 00:00.
        if (sqrt(meanCos * meanCos + meanSin * meanSin) < 1e-6) {
            return times.first()
        }

        var meanAngle = atan2(meanSin, meanCos)
        if (meanAngle < 0.0) {
            meanAngle += TWO_PI
        }

        val meanMinutes = (Math.round((meanAngle / TWO_PI) * MINUTES_PER_DAY) % 1440L).toInt()
        val hours = (meanMinutes / 60) % 24
        val minutes = meanMinutes % 60

        return LocalTime.of(hours, minutes)
    }

    /**
     * Calculates circular standard deviation in minutes.
     *
     * Measures the day-to-day dispersion/variability of sleep or wake timing.
     * Lower values indicate high schedule consistency; higher values indicate schedule irregularity.
     *
     * @return Standard deviation in minutes, or null if fewer than 2 observations exist.
     */
    fun calculateCircularStandardDeviationMinutes(times: List<LocalTime>): Double? {
        if (times.size < 2) return null

        var sumCos = 0.0
        var sumSin = 0.0

        for (time in times) {
            val minutes = time.hour * 60.0 + time.minute
            val angle = (minutes / MINUTES_PER_DAY) * TWO_PI
            sumCos += cos(angle)
            sumSin += sin(angle)
        }

        val meanCos = sumCos / times.size
        val meanSin = sumSin / times.size
        val r = sqrt(meanCos * meanCos + meanSin * meanSin).coerceIn(1e-6, 1.0)
        if (r >= 1.0 - 1e-7) {
            return 0.0
        }

        // Circular standard deviation: s = sqrt(-2 * ln(R)) * (1440 / 2pi)
        val sRadians = sqrt(maxOf(0.0, -2.0 * ln(r)))
        val sMinutes = (sRadians / TWO_PI) * MINUTES_PER_DAY

        return Math.round(sMinutes * 10.0) / 10.0
    }
}
