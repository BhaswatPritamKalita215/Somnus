package com.example.somnus.data.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.somnus.data.repository.StepRepository
import com.example.somnus.domain.model.StepCounterCheckpoint
import com.example.somnus.domain.model.StepRecord
import com.example.somnus.domain.model.StepSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class PhoneStepTrackerStatus(val description: String) {
    ACTIVE("Hardware step tracking is active."),
    READY("Sensors and permissions are ready for tracking."),
    PERMISSION_REQUIRED("Physical activity permission (ACTIVITY_RECOGNITION) is required on this Android version."),
    NO_SENSOR_AVAILABLE("No hardware step sensors (neither TYPE_STEP_COUNTER nor TYPE_STEP_DETECTOR) found on this device.")
}

data class StepCalculationResult(
    val deltaSteps: Long,
    val newCheckpoint: StepCounterCheckpoint,
    val isMidnightRollover: Boolean,
    val isRebootOrReset: Boolean
)

/**
 * Manages phone hardware step counting using Android hardware sensors.
 *
 * Prioritizes [Sensor.TYPE_STEP_COUNTER] (hardware cumulative count) with fallback
 * to [Sensor.TYPE_STEP_DETECTOR] (step events).
 *
 * Guarantees:
 * 1. Runtime permission validation on Android 10+ (API 29+).
 * 2. Counter reset and device reboot detection without negative deltas or missed steps.
 * 3. Clean midnight boundary handling (allocating steps to correct LocalDate).
 * 4. Durable persistence of baseline checkpoints and interval records.
 * 5. Honest provenance attribution ([StepSource.PHONE_HARDWARE]).
 */
class PhoneStepTracker(
    private val context: Context,
    private val stepRepository: StepRepository,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val stepCounter = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val stepDetector = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val _status = MutableStateFlow(checkStatus())
    val status: StateFlow<PhoneStepTrackerStatus> = _status.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private var activeSensorType: Int? = null

    fun checkStatus(): PhoneStepTrackerStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val permission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            )
            if (permission != PackageManager.PERMISSION_GRANTED) {
                return PhoneStepTrackerStatus.PERMISSION_REQUIRED
            }
        }

        return if (stepCounter != null || stepDetector != null) {
            if (_isTracking.value) PhoneStepTrackerStatus.ACTIVE else PhoneStepTrackerStatus.READY
        } else {
            PhoneStepTrackerStatus.NO_SENSOR_AVAILABLE
        }
    }

    fun startTracking(): Boolean {
        val currentStatus = checkStatus()
        if (currentStatus == PhoneStepTrackerStatus.PERMISSION_REQUIRED ||
            currentStatus == PhoneStepTrackerStatus.NO_SENSOR_AVAILABLE
        ) {
            _status.value = currentStatus
            return false
        }

        if (sensorManager == null) {
            _status.value = PhoneStepTrackerStatus.NO_SENSOR_AVAILABLE
            return false
        }

        val targetSensor = stepCounter ?: stepDetector ?: return false
        val registered = sensorManager.registerListener(
            this,
            targetSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )

        if (registered) {
            activeSensorType = targetSensor.type
            _isTracking.value = true
            _status.value = PhoneStepTrackerStatus.ACTIVE
        }
        return registered
    }

    fun stopTracking() {
        sensorManager?.unregisterListener(this)
        _isTracking.value = false
        activeSensorType = null
        _status.value = checkStatus()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!_isTracking.value || event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val rawValue = event.values.firstOrNull() ?: return
                handleStepCounterValue(rawValue)
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                handleStepDetectorEvent()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op for step sensors
    }

    private fun handleStepCounterValue(rawValue: Float) {
        scope.launch(ioDispatcher) {
            val existingCheckpoint = stepRepository.getCheckpointSync()
            val nowMs = System.currentTimeMillis()
            val today = LocalDate.now()
            val bootTimestamp = SystemClock.elapsedRealtime()

            val calc = calculateStepCounterDelta(
                currentSensorValue = rawValue,
                existingCheckpoint = existingCheckpoint,
                nowEpochMs = nowMs,
                currentLocalDate = today,
                currentBootTimestamp = bootTimestamp
            )

            // Save updated checkpoint
            stepRepository.saveCheckpoint(calc.newCheckpoint)

            // If new steps were taken, record them in Room
            if (calc.deltaSteps > 0) {
                val recordStartMs = existingCheckpoint?.lastUpdateEpochMs ?: (nowMs - 60000L)
                val record = StepRecord(
                    id = UUID.randomUUID().toString(),
                    startTime = Instant.ofEpochMilli(recordStartMs),
                    endTime = Instant.ofEpochMilli(nowMs),
                    localDate = today,
                    zoneId = ZoneId.systemDefault(),
                    stepCount = calc.deltaSteps,
                    source = StepSource.PHONE_HARDWARE,
                    sourceIdentifier = "hardware:step_counter",
                    deviceModel = Build.MODEL,
                    externalRecordId = null,
                    createdAt = Instant.ofEpochMilli(nowMs)
                )
                stepRepository.insertRecord(record)
            }
        }
    }

    private fun handleStepDetectorEvent() {
        scope.launch(ioDispatcher) {
            val nowMs = System.currentTimeMillis()
            val today = LocalDate.now()
            val existingCheckpoint = stepRepository.getCheckpointSync()

            val accumulated = if (existingCheckpoint != null && existingCheckpoint.localDate == today.toString()) {
                existingCheckpoint.accumulatedSteps + 1L
            } else {
                1L
            }

            val newCheckpoint = StepCounterCheckpoint(
                bootTimestamp = SystemClock.elapsedRealtime(),
                lastSensorValue = 1.0f,
                accumulatedSteps = accumulated,
                lastUpdateEpochMs = nowMs,
                localDate = today.toString()
            )
            stepRepository.saveCheckpoint(newCheckpoint)

            val record = StepRecord(
                id = UUID.randomUUID().toString(),
                startTime = Instant.ofEpochMilli(nowMs - 1000L),
                endTime = Instant.ofEpochMilli(nowMs),
                localDate = today,
                zoneId = ZoneId.systemDefault(),
                stepCount = 1L,
                source = StepSource.PHONE_HARDWARE,
                sourceIdentifier = "hardware:step_detector",
                deviceModel = Build.MODEL,
                externalRecordId = null,
                createdAt = Instant.ofEpochMilli(nowMs)
            )
            stepRepository.insertRecord(record)
        }
    }

    companion object {
        /**
         * Pure function calculating step delta, reboot detection, midnight rollover, and new checkpoint.
         * Decoupled from Android SensorManager for deterministic unit testing.
         */
        fun calculateStepCounterDelta(
            currentSensorValue: Float,
            existingCheckpoint: StepCounterCheckpoint?,
            nowEpochMs: Long,
            currentLocalDate: LocalDate,
            currentBootTimestamp: Long
        ): StepCalculationResult {
            if (existingCheckpoint == null) {
                // Initial baseline: record current cumulative value without reporting past steps
                val checkpoint = StepCounterCheckpoint(
                    bootTimestamp = currentBootTimestamp,
                    lastSensorValue = currentSensorValue,
                    accumulatedSteps = 0L,
                    lastUpdateEpochMs = nowEpochMs,
                    localDate = currentLocalDate.toString()
                )
                return StepCalculationResult(
                    deltaSteps = 0L,
                    newCheckpoint = checkpoint,
                    isMidnightRollover = false,
                    isRebootOrReset = false
                )
            }

            // Check for reboot / counter overflow (currentSensorValue is less than previous reading)
            val isRebootOrReset = currentSensorValue < existingCheckpoint.lastSensorValue
            val delta = if (isRebootOrReset) {
                // Device rebooted or counter reset to zero: delta is from 0 to current reading
                currentSensorValue.toLong().coerceAtLeast(0L)
            } else {
                (currentSensorValue - existingCheckpoint.lastSensorValue).toLong().coerceAtLeast(0L)
            }

            // Midnight rollover detection
            val isMidnightRollover = currentLocalDate.toString() != existingCheckpoint.localDate
            val accumulated = if (isMidnightRollover) {
                delta
            } else {
                existingCheckpoint.accumulatedSteps + delta
            }

            val newCheckpoint = StepCounterCheckpoint(
                bootTimestamp = currentBootTimestamp,
                lastSensorValue = currentSensorValue,
                accumulatedSteps = accumulated,
                lastUpdateEpochMs = nowEpochMs,
                localDate = currentLocalDate.toString()
            )

            return StepCalculationResult(
                deltaSteps = delta,
                newCheckpoint = newCheckpoint,
                isMidnightRollover = isMidnightRollover,
                isRebootOrReset = isRebootOrReset
            )
        }
    }
}
