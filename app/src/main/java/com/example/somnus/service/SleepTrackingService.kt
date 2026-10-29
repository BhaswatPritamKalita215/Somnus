package com.example.somnus.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.somnus.MainActivity
import com.example.somnus.SomnusApplication
import com.example.somnus.data.sensor.InterruptionReason
import com.example.somnus.data.sensor.PhoneSensorTracker
import com.example.somnus.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Foreground service providing reliable overnight sensor data collection for Somnus.
 *
 * Technical Justification:
 * - Uses [ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH] under Android 14+ (targetSdk 34).
 * - Health type is the official Android category for continuous wellness, fitness, and sleep tracking.
 * - Protects against aggressive background task termination during overnight screen-off periods.
 * - Employs a timed partial wake lock to guarantee accelerometer sensor event delivery.
 * - Honestly records interruptions caused by battery saver, low battery, system reclamation, and 12h timeout.
 */
class SleepTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var isReceiverRegistered = false
    private var trackingStartTimeMillis: Long = 0L

    internal var trackerOverride: PhoneSensorTracker? = null
    internal var sessionManagerOverride: SessionManager? = null

    private val phoneSensorTracker: PhoneSensorTracker
        get() = trackerOverride
            ?: (applicationContext as? SomnusApplication)?.container?.phoneSensorTracker
            ?: PhoneSensorTracker(applicationContext)

    private val sessionManager: SessionManager?
        get() = sessionManagerOverride
            ?: (applicationContext as? SomnusApplication)?.container?.sessionManager

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_LOW -> {
                    phoneSensorTracker.recordInterruption(
                        InterruptionReason.LOW_BATTERY_SHUTDOWN,
                        "Battery level dropped critically low (< 15%)."
                    )
                }
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                    if (pm?.isPowerSaveMode == true) {
                        phoneSensorTracker.recordInterruption(
                            InterruptionReason.BATTERY_SAVER_ENABLED,
                            "Battery Saver mode activated by system."
                        )
                    } else {
                        phoneSensorTracker.resolveInterruption()
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    phoneSensorTracker.recordInterruption(
                        InterruptionReason.SCREEN_USER_INTERACTION,
                        "Screen illuminated / user interaction detected."
                    )
                }
                Intent.ACTION_SCREEN_OFF -> {
                    phoneSensorTracker.resolveInterruption()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_TRACKING

        when (action) {
            ACTION_START_TRACKING -> {
                val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID)
                if (sessionId.isNullOrBlank()) {
                    Log.w(TAG, "Cannot start tracking without a valid sessionId")
                    stopSelf()
                    return START_NOT_STICKY
                }
                val startTimeMillis = intent.getLongExtra(EXTRA_START_TIME, System.currentTimeMillis())
                handleStartTracking(sessionId, startTimeMillis)
            }
            ACTION_STOP_TRACKING -> {
                val notes = intent?.getStringExtra(EXTRA_NOTES)
                val stopSource = intent?.getStringExtra(EXTRA_STOP_SOURCE)
                handleStopTracking(notes, stopSource)
            }
            ACTION_DISCARD_TRACKING -> {
                handleDiscardTracking()
            }
            else -> {
                Log.w(TAG, "Unknown action received: $action")
            }
        }

        return START_STICKY
    }

    private fun handleStartTracking(sessionId: String, startTimeMillis: Long) {
        trackingStartTimeMillis = startTimeMillis

        // Idempotent start: if already running the same session, update notification and continue
        if (_isServiceRunning.value && _currentSessionId.value == sessionId) {
            val notification = buildOngoingNotification(sessionId, startTimeMillis)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(NOTIFICATION_ID, notification)
            return
        }

        val notification = buildOngoingNotification(sessionId, startTimeMillis)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting foreground service: ${e.message}", e)
            phoneSensorTracker.recordInterruption(
                InterruptionReason.SYSTEM_KILLED_SERVICE,
                "Foreground service permission denied or restricted: ${e.message}"
            )
            _isServiceRunning.value = false
            _isTrackingInterrupted.value = true
            stopSelf()
            return
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting foreground service: ${e.message}", e)
            _isServiceRunning.value = false
            _isTrackingInterrupted.value = true
            stopSelf()
            return
        }

        acquireWakeLock()
        registerPowerReceiver()

        val sensorStarted = phoneSensorTracker.startTracking(sessionId)
        if (!sensorStarted) {
            Log.w(TAG, "Phone sensor tracker failed to register accelerometer")
        }

        startEpochTicker()

        _isServiceRunning.value = true
        _currentSessionId.value = sessionId
        _isTrackingInterrupted.value = false
    }

    private fun handleStopTracking(notes: String?, stopSource: String? = null) {
        if (!_isServiceRunning.value && !phoneSensorTracker.isTracking.value) {
            stopForegroundAndCleanUp()
            stopSelf()
            return
        }

        stopEpochTicker()
        phoneSensorTracker.stopTracking()
        unregisterPowerReceiver()
        releaseWakeLock()

        stopForegroundAndCleanUp()

        // Only end session directly if stopped from notification shade.
        // If stopped from app UI, HomeViewModel manages the session end.
        if (stopSource != STOP_SOURCE_APP_UI) {
            sessionManager?.let { sm ->
                if (sm.activeSession.value != null) {
                    serviceScope.launch {
                        try {
                            sm.endSession(notes ?: "Completed from notification")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error finalizing session in repository: ${e.message}", e)
                        }
                    }
                }
            }
        }

        trackingStartTimeMillis = 0L
        _isServiceRunning.value = false
        _currentSessionId.value = null
        _isTrackingInterrupted.value = false

        stopSelf()
    }

    private fun handleDiscardTracking() {
        stopEpochTicker()
        phoneSensorTracker.stopTracking()
        unregisterPowerReceiver()
        releaseWakeLock()

        stopForegroundAndCleanUp()

        sessionManager?.let { sm ->
            if (sm.activeSession.value != null) {
                serviceScope.launch {
                    try {
                        sm.discardActiveSession()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error discarding active session: ${e.message}", e)
                    }
                }
            }
        }

        trackingStartTimeMillis = 0L
        _isServiceRunning.value = false
        _currentSessionId.value = null
        _isTrackingInterrupted.value = false

        stopSelf()
    }

    private fun startEpochTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(EPOCH_TICK_INTERVAL_MS)
                try {
                    phoneSensorTracker.completeCurrentEpoch()

                    // Ensure tracking does not claim continuous monitoring beyond the 12-hour wake-lock safety window
                    if (checkTrackingDuration(System.currentTimeMillis())) {
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during periodic epoch completion: ${e.message}", e)
                }
            }
        }
    }

    internal fun checkTrackingDuration(currentTimeMillis: Long): Boolean {
        if (trackingStartTimeMillis > 0 &&
            (currentTimeMillis - trackingStartTimeMillis) >= MAX_WAKE_LOCK_DURATION_MS
        ) {
            Log.w(TAG, "Maximum tracking duration (12 hours) reached. Halting sensor collection.")
            phoneSensorTracker.recordInterruption(
                InterruptionReason.BATTERY_SAVER_ENABLED,
                "Maximum tracking duration (12 hours) elapsed. Wake lock released and collection halted."
            )
            handleStopTracking("Completed: maximum 12-hour limit reached", STOP_SOURCE_NOTIFICATION)
            return true
        }
        return false
    }

    private fun stopEpochTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)?.apply {
                setReferenceCounted(false)
            }
        }
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire()
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                try {
                    it.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Exception releasing wake lock: ${e.message}")
                }
            }
        }
        wakeLock = null
    }

    private fun registerPowerReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            try {
                registerReceiver(powerReceiver, filter)
                isReceiverRegistered = true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register power receiver: ${e.message}")
            }
        }
    }

    private fun unregisterPowerReceiver() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(powerReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister power receiver: ${e.message}")
            }
            isReceiverRegistered = false
        }
    }

    private fun stopForegroundAndCleanUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildOngoingNotification(sessionId: String, startTimeMillis: Long): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN_APP,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SleepTrackingService::class.java).apply {
            action = ACTION_STOP_TRACKING
            putExtra(EXTRA_STOP_SOURCE, STOP_SOURCE_NOTIFICATION)
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_STOP,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val iconRes = android.R.drawable.ic_lock_idle_alarm

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle("Somnus Sleep Tracking")
            .setContentText("Monitoring overnight movement via phone sensors")
            .setSubText("Tracking active")
            .setOngoing(true)
            .setUsesChronometer(true)
            .setWhen(startTimeMillis)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Tracking",
                stopPendingIntent
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()

        if (_isServiceRunning.value) {
            // Service destroyed unexpectedly by the operating system (e.g. low memory killer)
            phoneSensorTracker.recordInterruption(
                InterruptionReason.SYSTEM_KILLED_SERVICE,
                "Service destroyed by OS while tracking was active."
            )
            phoneSensorTracker.stopTracking()
            _isTrackingInterrupted.value = true
        }

        stopEpochTicker()
        unregisterPowerReceiver()
        releaseWakeLock()
        stopForegroundAndCleanUp()

        trackingStartTimeMillis = 0L
        _isServiceRunning.value = false
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val TAG = "SleepTrackingService"
        const val CHANNEL_ID = "somnus_sleep_tracking_channel"
        const val CHANNEL_NAME = "Sleep Tracking"
        const val CHANNEL_DESCRIPTION = "Ongoing notification for overnight sleep movement tracking"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START_TRACKING = "com.example.somnus.action.START_SLEEP_TRACKING"
        const val ACTION_STOP_TRACKING = "com.example.somnus.action.STOP_SLEEP_TRACKING"
        const val ACTION_DISCARD_TRACKING = "com.example.somnus.action.DISCARD_SLEEP_TRACKING"

        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_START_TIME = "extra_start_time"
        const val EXTRA_NOTES = "extra_notes"
        const val EXTRA_STOP_SOURCE = "extra_stop_source"

        const val STOP_SOURCE_APP_UI = "source_app_ui"
        const val STOP_SOURCE_NOTIFICATION = "source_notification"

        private const val REQUEST_CODE_OPEN_APP = 101
        private const val REQUEST_CODE_STOP = 102
        private const val WAKE_LOCK_TAG = "Somnus:SleepTrackingWakeLock"
        const val MAX_WAKE_LOCK_DURATION_MS = 12 * 60 * 60 * 1000L // 12 hours max
        private const val EPOCH_TICK_INTERVAL_MS = 60_000L // 60 seconds

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _isTrackingInterrupted = MutableStateFlow(false)
        val isTrackingInterrupted: StateFlow<Boolean> = _isTrackingInterrupted.asStateFlow()

        private val _currentSessionId = MutableStateFlow<String?>(null)
        val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

        fun start(
            context: Context,
            sessionId: String,
            startTime: Instant = Instant.now(),
            notes: String? = null
        ) {
            val intent = Intent(context, SleepTrackingService::class.java).apply {
                action = ACTION_START_TRACKING
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_START_TIME, startTime.toEpochMilli())
                putExtra(EXTRA_NOTES, notes)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context, notes: String? = null, isFromAppUi: Boolean = false) {
            val intent = Intent(context, SleepTrackingService::class.java).apply {
                action = ACTION_STOP_TRACKING
                putExtra(EXTRA_NOTES, notes)
                putExtra(EXTRA_STOP_SOURCE, if (isFromAppUi) STOP_SOURCE_APP_UI else STOP_SOURCE_NOTIFICATION)
            }
            context.startService(intent)
        }

        fun discard(context: Context) {
            val intent = Intent(context, SleepTrackingService::class.java).apply {
                action = ACTION_DISCARD_TRACKING
            }
            context.startService(intent)
        }

        fun resetTestState() {
            _isServiceRunning.value = false
            _isTrackingInterrupted.value = false
            _currentSessionId.value = null
        }
    }
}
