package com.example.somnus.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.somnus.data.sensor.InterruptionReason
import com.example.somnus.data.sensor.PhoneSensorTracker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowNotificationManager
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SleepTrackingServiceTest {

    private lateinit var context: Context
    private lateinit var serviceController: ServiceController<SleepTrackingService>
    private lateinit var service: SleepTrackingService
    private lateinit var sensorTracker: PhoneSensorTracker
    private lateinit var notificationManager: NotificationManager
    private lateinit var shadowNotificationManager: ShadowNotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        SleepTrackingService.resetTestState()

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val shadowSensorManager: ShadowSensorManager = shadowOf(sensorManager)
        val accelerometer = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        shadowSensorManager.addSensor(accelerometer)

        sensorTracker = PhoneSensorTracker(context)

        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowNotificationManager = shadowOf(notificationManager)

        serviceController = Robolectric.buildService(SleepTrackingService::class.java)
        service = serviceController.get()
        service.trackerOverride = sensorTracker
        serviceController.create()
    }

    @After
    fun tearDown() {
        try {
            serviceController.destroy()
        } catch (e: Exception) {
            // Ignore if already destroyed
        }
        SleepTrackingService.resetTestState()
    }

    @Test
    fun onStartCommand_withStartTrackingAction_startsForegroundAndSetsRunningState() {
        val intent = Intent(context, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_START_TRACKING
            putExtra(SleepTrackingService.EXTRA_SESSION_ID, "session-test-123")
            putExtra(SleepTrackingService.EXTRA_START_TIME, 1700000000000L)
        }

        service.onStartCommand(intent, 0, 1)

        assertTrue(SleepTrackingService.isServiceRunning.value)
        assertEquals("session-test-123", SleepTrackingService.currentSessionId.value)
        assertFalse(SleepTrackingService.isTrackingInterrupted.value)

        // Notification channel was created
        val channel = notificationManager.getNotificationChannel(SleepTrackingService.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(SleepTrackingService.CHANNEL_NAME, channel?.name)

        // Notification was posted
        val notification = shadowNotificationManager.getNotification(SleepTrackingService.NOTIFICATION_ID)
        assertNotNull(notification)
    }

    @Test
    fun onStartCommand_duplicateStartCommands_isIdempotent() {
        val intent = Intent(context, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_START_TRACKING
            putExtra(SleepTrackingService.EXTRA_SESSION_ID, "session-idempotent-test")
        }

        service.onStartCommand(intent, 0, 1)
        assertTrue(SleepTrackingService.isServiceRunning.value)

        // Second duplicate command
        service.onStartCommand(intent, 0, 2)
        assertTrue(SleepTrackingService.isServiceRunning.value)
        assertEquals("session-idempotent-test", SleepTrackingService.currentSessionId.value)
    }

    @Test
    fun onStartCommand_withStopTrackingAction_stopsTrackingAndResetsState() {
        val startIntent = Intent(context, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_START_TRACKING
            putExtra(SleepTrackingService.EXTRA_SESSION_ID, "session-stop-test")
        }
        service.onStartCommand(startIntent, 0, 1)
        assertTrue(SleepTrackingService.isServiceRunning.value)

        val stopIntent = Intent(context, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_STOP_TRACKING
            putExtra(SleepTrackingService.EXTRA_NOTES, "Woke up well rested")
        }
        service.onStartCommand(stopIntent, 0, 2)

        assertFalse(SleepTrackingService.isServiceRunning.value)
        assertNull(SleepTrackingService.currentSessionId.value)
        assertFalse(SleepTrackingService.isTrackingInterrupted.value)
    }

    @Test
    fun onStartCommand_duplicateStopCommands_doesNotThrow() {
        val stopIntent = Intent(context, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_STOP_TRACKING
        }
        // Stopping when not tracking
        service.onStartCommand(stopIntent, 0, 1)
        assertFalse(SleepTrackingService.isServiceRunning.value)

        // Second stop command
        service.onStartCommand(stopIntent, 0, 2)
        assertFalse(SleepTrackingService.isServiceRunning.value)
    }

    @Test
    fun onDestroy_whileTrackingActive_recordsSystemKilledServiceInterruption() {
        val startIntent = Intent(context, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_START_TRACKING
            putExtra(SleepTrackingService.EXTRA_SESSION_ID, "session-kill-test")
        }
        service.onStartCommand(startIntent, 0, 1)
        assertTrue(SleepTrackingService.isServiceRunning.value)

        // Simulate OS destroying service while tracking was still active
        serviceController.destroy()

        assertFalse(SleepTrackingService.isServiceRunning.value)
        assertTrue(SleepTrackingService.isTrackingInterrupted.value)

        val interruptions = sensorTracker.getRecordedInterruptions()
        assertTrue(interruptions.any { it.reason == InterruptionReason.SYSTEM_KILLED_SERVICE })
    }

    @Test
    fun companionHelpers_startStopDiscard_createAccurateIntents() {
        val now = Instant.ofEpochMilli(1700000000000L)

        SleepTrackingService.start(context, "session-xyz", now, "Night notes")
        val startedIntent = shadowOf(context as android.app.Application).nextStartedService
        assertNotNull(startedIntent)
        assertEquals(SleepTrackingService.ACTION_START_TRACKING, startedIntent.action)
        assertEquals("session-xyz", startedIntent.getStringExtra(SleepTrackingService.EXTRA_SESSION_ID))
        assertEquals(1700000000000L, startedIntent.getLongExtra(SleepTrackingService.EXTRA_START_TIME, 0))
        assertEquals("Night notes", startedIntent.getStringExtra(SleepTrackingService.EXTRA_NOTES))

        SleepTrackingService.stop(context, "Stopping now")
        val stoppedIntent = shadowOf(context as android.app.Application).nextStartedService
        assertNotNull(stoppedIntent)
        assertEquals(SleepTrackingService.ACTION_STOP_TRACKING, stoppedIntent.action)
        assertEquals("Stopping now", stoppedIntent.getStringExtra(SleepTrackingService.EXTRA_NOTES))

        SleepTrackingService.discard(context)
        val discardIntent = shadowOf(context as android.app.Application).nextStartedService
        assertNotNull(discardIntent)
        assertEquals(SleepTrackingService.ACTION_DISCARD_TRACKING, discardIntent.action)
    }

    @Test
    fun checkTrackingDuration_whenExceeds12Hours_haltsTrackingAndRecordsInterruption() {
        val startTime = 1700000000000L
        val startIntent = Intent(context, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_START_TRACKING
            putExtra(SleepTrackingService.EXTRA_SESSION_ID, "session-12h-test")
            putExtra(SleepTrackingService.EXTRA_START_TIME, startTime)
        }
        service.onStartCommand(startIntent, 0, 1)
        assertTrue(SleepTrackingService.isServiceRunning.value)

        // Before 12 hours: should return false and keep running
        val under12Hours = startTime + (11 * 3600 * 1000L)
        val underLimit = service.checkTrackingDuration(under12Hours)
        assertFalse(underLimit)
        assertTrue(SleepTrackingService.isServiceRunning.value)

        // At or after 12 hours (43_200_000 ms): should halt tracking and record interruption
        val at12Hours = startTime + SleepTrackingService.MAX_WAKE_LOCK_DURATION_MS
        val overLimit = service.checkTrackingDuration(at12Hours)
        assertTrue(overLimit)
        assertFalse(SleepTrackingService.isServiceRunning.value)

        val interruptions = sensorTracker.getRecordedInterruptions()
        assertTrue(interruptions.any { it.reason == InterruptionReason.BATTERY_SAVER_ENABLED })
    }

    @Test
    fun companionHelpers_stopWithAppUiSource_setsExtraStopSource() {
        SleepTrackingService.stop(context, "Stopped from app UI", isFromAppUi = true)
        val stoppedIntent = shadowOf(context as android.app.Application).nextStartedService
        assertNotNull(stoppedIntent)
        assertEquals(SleepTrackingService.ACTION_STOP_TRACKING, stoppedIntent.action)
        assertEquals(SleepTrackingService.STOP_SOURCE_APP_UI, stoppedIntent.getStringExtra(SleepTrackingService.EXTRA_STOP_SOURCE))
    }
}
