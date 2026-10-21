package com.example.somnus.data.sensor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.Collections
import java.util.UUID

/**
 * Manages phone hardware sensor tracking, windowed aggregation, battery monitoring,
 * and tracking interruption auditing.
 */
class PhoneSensorTracker(
    private val context: Context,
    private val sensorManager: SensorManager? = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager,
    private val powerManager: PowerManager? = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
) : SensorEventListener {

    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val aggregator = MovementWindowAggregator()
    private val _completedEpochs: MutableList<MovementEpoch> = Collections.synchronizedList(mutableListOf())
    private val _interruptions: MutableList<TrackingInterruption> = Collections.synchronizedList(mutableListOf())
    private var activeInterruption: TrackingInterruption? = null

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private var currentSessionId: String? = null

    val currentEpochCount: Int
        get() = _completedEpochs.size

    val currentInterruptionCount: Int
        get() = _interruptions.size

    val activeInterruptionReason: InterruptionReason?
        get() = activeInterruption?.reason

    fun getCurrentSessionId(): String? = currentSessionId

    fun getSensorAvailability(): SensorAvailabilityReport {
        val hasAccel = accelerometer != null
        val isBatterySaver = powerManager?.isPowerSaveMode ?: false

        val batteryStatusIntent = try {
            context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
        } catch (e: Exception) {
            null
        }
        val level = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1

        val status = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return SensorAvailabilityReport(
            hasAccelerometer = hasAccel,
            sensorName = accelerometer?.name,
            sensorVendor = accelerometer?.vendor,
            isBatterySaverActive = isBatterySaver,
            batteryLevelPercent = pct,
            isCharging = isCharging
        )
    }

    /**
     * Starts tracking motion samples for the given session.
     * Idempotent: If already tracking for the same session, returns true without resetting state.
     */
    fun startTracking(sessionId: String): Boolean {
        if (_isTracking.value && currentSessionId == sessionId) {
            return true
        }

        if (accelerometer == null || sensorManager == null) {
            recordInterruption(InterruptionReason.SENSOR_INACTIVE, "No hardware accelerometer available.")
            return false
        }

        currentSessionId = sessionId
        _completedEpochs.clear()
        _interruptions.clear()
        activeInterruption = null
        aggregator.reset(Instant.now())

        val registered = sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL
        )

        if (registered) {
            _isTracking.value = true
        } else {
            recordInterruption(InterruptionReason.SENSOR_INACTIVE, "Failed to register accelerometer listener.")
        }

        return registered
    }

    /**
     * Completes and stores the current windowed epoch.
     * Called periodically during tracking and immediately upon stopping.
     */
    fun completeCurrentEpoch(): MovementEpoch? {
        if (!_isTracking.value) return null
        val isScreenOn = powerManager?.isInteractive ?: false
        val batteryStatus = getSensorAvailability()
        val epoch = aggregator.completeEpoch(
            endTime = Instant.now(),
            isScreenOn = isScreenOn,
            isCharging = batteryStatus.isCharging
        )
        _completedEpochs.add(epoch)
        return epoch
    }

    /**
     * Stops tracking, completes any pending movement epoch, unregisters sensor listeners,
     * and closes any ongoing interruption.
     * Idempotent: If already stopped, returns recorded epochs safely.
     */
    fun stopTracking(): List<MovementEpoch> {
        if (!_isTracking.value) {
            return _completedEpochs.toList()
        }

        // Finalize last epoch window if active
        completeCurrentEpoch()

        sensorManager?.unregisterListener(this)
        _isTracking.value = false

        // Close any ongoing interruption
        closeActiveInterruption()

        return _completedEpochs.toList()
    }

    @Synchronized
    fun recordInterruption(reason: InterruptionReason, notes: String? = null) {
        closeActiveInterruption()
        val interruption = TrackingInterruption(
            id = UUID.randomUUID().toString(),
            startTime = Instant.now(),
            endTime = null,
            reason = reason,
            notes = notes
        )
        activeInterruption = interruption
        _interruptions.add(interruption)
    }

    @Synchronized
    fun resolveInterruption() {
        closeActiveInterruption()
    }

    @Synchronized
    private fun closeActiveInterruption() {
        val ongoing = activeInterruption ?: return
        val index = _interruptions.indexOfFirst { it.id == ongoing.id }
        if (index != -1) {
            _interruptions[index] = ongoing.copy(endTime = Instant.now())
        }
        activeInterruption = null
    }

    fun getRecordedEpochs(): List<MovementEpoch> = _completedEpochs.toList()
    fun getRecordedInterruptions(): List<TrackingInterruption> = _interruptions.toList()

    override fun onSensorChanged(event: SensorEvent?) {
        if (!_isTracking.value || event == null) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            aggregator.addSample(event.values[0], event.values[1], event.values[2])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op for sleep tracking
    }
}
