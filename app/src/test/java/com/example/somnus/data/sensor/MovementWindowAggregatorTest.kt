package com.example.somnus.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class MovementWindowAggregatorTest {

    private lateinit var aggregator: MovementWindowAggregator

    @Before
    fun setup() {
        aggregator = MovementWindowAggregator()
    }

    @Test
    fun completeEpoch_withZeroSamples_categorizesAsInterruptedUnmonitored() {
        val now = Instant.now()
        val epoch = aggregator.completeEpoch(now.plusSeconds(60))

        assertEquals(MovementCategory.INTERRUPTED_UNMONITORED, epoch.category)
        assertEquals(0.0, epoch.meanMagnitude, 0.001)
        assertEquals(0.0, epoch.stdDevMagnitude, 0.001)
    }

    @Test
    fun completeEpoch_withConstantGravity_categorizesAsStillness() {
        val start = Instant.now()
        aggregator.reset(start)

        // Simulate 60 Hz 1G earth gravity: x=0, y=0, z=9.81
        repeat(60) {
            aggregator.addSample(0.0f, 0.0f, 9.81f)
        }

        val epoch = aggregator.completeEpoch(start.plusSeconds(60))
        assertEquals(MovementCategory.STILLNESS, epoch.category)
        assertEquals(9.81, epoch.meanMagnitude, 0.01)
        assertEquals(0.0, epoch.stdDevMagnitude, 0.001)
        assertEquals(9.81, epoch.peakMagnitude, 0.01)
    }

    @Test
    fun completeEpoch_afterThirtyContinuousStillnessEpochs_transitionsToOffPersonStationary() {
        val start = Instant.now()
        aggregator.reset(start)

        var lastEpoch: MovementEpoch? = null
        for (i in 0 until 35) {
            repeat(10) {
                aggregator.addSample(0.0f, 0.0f, 9.81f)
            }
            lastEpoch = aggregator.completeEpoch(start.plusSeconds((i + 1) * 60L))
        }

        assertEquals(MovementCategory.OFF_PERSON_STATIONARY, lastEpoch?.category)
    }

    @Test
    fun completeEpoch_withMildFluctuations_categorizesAsMinorRestlessness() {
        val start = Instant.now()
        aggregator.reset(start)

        // Alternating small bed movements causing acceleration fluctuations
        for (i in 0 until 60) {
            val z = if (i % 2 == 0) 9.65f else 9.95f
            aggregator.addSample(0.0f, 0.0f, z)
        }

        val epoch = aggregator.completeEpoch(start.plusSeconds(60))
        assertEquals(MovementCategory.MINOR_RESTLESSNESS, epoch.category)
        assertTrue("stdDev should be > 0.05", epoch.stdDevMagnitude > 0.05)
        assertTrue("stdDev should be < 0.35", epoch.stdDevMagnitude < 0.35)
    }

    @Test
    fun completeEpoch_withLargeMotion_categorizesAsActiveMovement() {
        val start = Instant.now()
        aggregator.reset(start)

        // Large physical handling / walking motion
        aggregator.addSample(3.5f, 5.0f, 14.0f)
        aggregator.addSample(-4.0f, -2.0f, 8.0f)
        aggregator.addSample(6.0f, 8.0f, 16.0f)

        val epoch = aggregator.completeEpoch(start.plusSeconds(60))
        assertEquals(MovementCategory.ACTIVE_MOVEMENT, epoch.category)
        assertTrue("Peak magnitude should exceed 12 m/s^2", epoch.peakMagnitude > 12.0)
    }
}
