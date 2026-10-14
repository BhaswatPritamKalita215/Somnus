package com.example.somnus.data.sensor

import java.time.Instant
import kotlin.math.sqrt

/**
 * Constant-space online window aggregator for 3-axis accelerometer samples.
 * Utilizes Welford's algorithm to compute exact streaming mean and sample variance
 * without buffering individual samples in memory.
 */
class MovementWindowAggregator(
    private val epochDurationSeconds: Long = 60L
) {
    private var windowStartIndex: Long = 0L
    private var windowStartInstant: Instant = Instant.now()
    private var sampleCount: Long = 0L
    private var mean: Double = 0.0
    private var m2: Double = 0.0
    private var peak: Double = 0.0
    private var consecutiveStillnessEpochs: Int = 0

    fun reset(startTime: Instant = Instant.now()) {
        windowStartInstant = startTime
        sampleCount = 0L
        mean = 0.0
        m2 = 0.0
        peak = 0.0
        consecutiveStillnessEpochs = 0
    }

    /**
     * Ingests a raw 3-axis accelerometer sample (m/s^2).
     */
    fun addSample(x: Float, y: Float, z: Float) {
        val magnitude = sqrt((x * x + y * y + z * z).toDouble())
        sampleCount++
        val delta = magnitude - mean
        mean += delta / sampleCount
        val delta2 = magnitude - mean
        m2 += delta * delta2
        if (magnitude > peak) {
            peak = magnitude
        }
    }

    /**
     * Closes the current epoch window and produces a summarized [MovementEpoch].
     */
    fun completeEpoch(
        endTime: Instant,
        isScreenOn: Boolean = false,
        isCharging: Boolean = false
    ): MovementEpoch {
        val count = sampleCount
        val variance = if (count > 1) m2 / (count - 1) else 0.0
        val stdDev = sqrt(variance)

        val category = when {
            count == 0L -> MovementCategory.INTERRUPTED_UNMONITORED
            stdDev < 0.05 -> {
                consecutiveStillnessEpochs++
                if (consecutiveStillnessEpochs >= 30) {
                    MovementCategory.OFF_PERSON_STATIONARY
                } else {
                    MovementCategory.STILLNESS
                }
            }
            stdDev < 0.35 && peak < 12.0 -> {
                consecutiveStillnessEpochs = 0
                MovementCategory.MINOR_RESTLESSNESS
            }
            else -> {
                consecutiveStillnessEpochs = 0
                MovementCategory.ACTIVE_MOVEMENT
            }
        }

        val epoch = MovementEpoch(
            epochIndex = windowStartIndex,
            startTime = windowStartInstant,
            endTime = endTime,
            meanMagnitude = if (count > 0) mean else 0.0,
            stdDevMagnitude = stdDev,
            peakMagnitude = peak,
            category = category,
            isScreenOn = isScreenOn,
            isCharging = isCharging
        )

        // Reset state for next window
        windowStartIndex++
        windowStartInstant = endTime
        sampleCount = 0L
        mean = 0.0
        m2 = 0.0
        peak = 0.0

        return epoch
    }
}
