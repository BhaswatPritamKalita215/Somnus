package com.example.somnus.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PhoneSensorTrackerTest {

    private lateinit var context: Context
    private lateinit var sensorManager: SensorManager
    private lateinit var tracker: PhoneSensorTracker

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val shadowSensorManager: ShadowSensorManager = shadowOf(sensorManager)
        val accelerometer = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        shadowSensorManager.addSensor(accelerometer)

        tracker = PhoneSensorTracker(context)
    }

    @Test
    fun startTracking_whenAccelerometerPresent_registersListenerAndSetsTrackingTrue() {
        val started = tracker.startTracking("session-abc-123")
        assertTrue(started)
        assertTrue(tracker.isTracking.value)
        assertEquals("session-abc-123", tracker.getCurrentSessionId())
        assertEquals(0, tracker.currentEpochCount)
        assertEquals(0, tracker.currentInterruptionCount)
    }

    @Test
    fun startTracking_isIdempotent_whenCalledWithSameSessionId() {
        val firstStart = tracker.startTracking("session-dup-test")
        assertTrue(firstStart)

        // Complete one epoch to check that state is preserved
        tracker.completeCurrentEpoch()
        assertEquals(1, tracker.currentEpochCount)

        // Second start call with the same session ID
        val secondStart = tracker.startTracking("session-dup-test")
        assertTrue(secondStart)
        assertTrue(tracker.isTracking.value)
        assertEquals(1, tracker.currentEpochCount) // Did not wipe completed epochs
    }

    @Test
    fun startTracking_whenSensorManagerOrAccelerometerNull_recordsInterruptionAndReturnsFalse() {
        val nullSensorTracker = PhoneSensorTracker(
            context = context,
            sensorManager = null,
            powerManager = null
        )

        val result = nullSensorTracker.startTracking("session-no-sensor")
        assertFalse(result)
        assertFalse(nullSensorTracker.isTracking.value)
        assertEquals(1, nullSensorTracker.currentInterruptionCount)
        assertEquals(InterruptionReason.SENSOR_INACTIVE, nullSensorTracker.activeInterruptionReason)
    }

    @Test
    fun completeCurrentEpoch_whenTracking_addsEpochToCollection() {
        tracker.startTracking("session-epoch-test")
        assertEquals(0, tracker.currentEpochCount)

        val epoch = tracker.completeCurrentEpoch()
        assertNotNull(epoch)
        assertEquals(1, tracker.currentEpochCount)
        assertEquals(1, tracker.getRecordedEpochs().size)
    }

    @Test
    fun completeCurrentEpoch_whenNotTracking_returnsNull() {
        assertFalse(tracker.isTracking.value)
        val epoch = tracker.completeCurrentEpoch()
        assertNull(epoch)
        assertEquals(0, tracker.currentEpochCount)
    }

    @Test
    fun stopTracking_finalizesPendingEpochAndUnregisters() {
        tracker.startTracking("session-stop-test")
        assertTrue(tracker.isTracking.value)

        val epochs = tracker.stopTracking()
        assertFalse(tracker.isTracking.value)
        // stopTracking calls completeCurrentEpoch() to capture the in-flight window
        assertEquals(1, epochs.size)
        assertEquals(1, tracker.getRecordedEpochs().size)

        // Second stopTracking is idempotent and returns existing epochs safely
        val secondStopEpochs = tracker.stopTracking()
        assertFalse(tracker.isTracking.value)
        assertEquals(1, secondStopEpochs.size)
    }

    @Test
    fun interruptionLifecycle_recordsAndClosesInterruptionCorrectly() {
        tracker.startTracking("session-interrupt-test")

        tracker.recordInterruption(InterruptionReason.BATTERY_SAVER_ENABLED, "Battery saver engaged")
        assertEquals(1, tracker.currentInterruptionCount)
        assertEquals(InterruptionReason.BATTERY_SAVER_ENABLED, tracker.activeInterruptionReason)

        val interruptions = tracker.getRecordedInterruptions()
        assertEquals(1, interruptions.size)
        val first = interruptions[0]
        assertEquals(InterruptionReason.BATTERY_SAVER_ENABLED, first.reason)
        assertEquals("Battery saver engaged", first.notes)
        assertNull(first.endTime)

        // Resolving the interruption sets the endTime
        tracker.resolveInterruption()
        assertNull(tracker.activeInterruptionReason)
        val resolvedInterruptions = tracker.getRecordedInterruptions()
        assertEquals(1, resolvedInterruptions.size)
        assertNotNull(resolvedInterruptions[0].endTime)
    }

    @Test
    fun getSensorAvailability_returnsSensibleDefaults() {
        val report = tracker.getSensorAvailability()
        assertNotNull(report.limitationsNotice)
        assertTrue(report.limitationsNotice.contains("Phone sensor tracking"))
        // Accelerometer was added in @Before
        assertTrue(report.hasAccelerometer)
    }
}
