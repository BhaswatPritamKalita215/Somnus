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
import java.util.UUID

/**
 * Manages phone hardware sensor tracking, windowed aggregation, battery monitoring,
 * and tracking interruption auditing.
 */
class PhoneSensorTracker(
    private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val aggregator = MovementWindowAggregator()
    private val _completedEpochs = mutableListOf<MovementEpoch>()
    private val _interruptions = mutableListOf<TrackingInterruption>()
    private var activeInterruption: TrackingInterruption? = null

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private var currentSessionId: String? = null

    fun getSensorAvailability(): SensorAvailabilityReport {
        val hasAccel = accelerometer != null
        val isBatterySaver = powerManager?.isPowerSaveMode ?: false

        val batteryStatusIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
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

    fun startTracking(sessionId: String): Boolean {
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

    fun stopTracking(): List<MovementEpoch> {
        sensorManager?.unregisterListener(this)
        _isTracking.value = false

        // Close any ongoing interruption
        closeActiveInterruption()

        return _completedEpochs.toList()
    }

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

    fun resolveInterruption() {
        closeActiveInterruption()
    }

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
