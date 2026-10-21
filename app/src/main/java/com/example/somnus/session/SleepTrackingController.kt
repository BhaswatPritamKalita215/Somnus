package com.example.somnus.session

import android.content.Context
import com.example.somnus.service.SleepTrackingService
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

/**
 * Controller abstraction to start, stop, discard, and observe overnight sleep tracking.
 * Decouples ViewModels from direct Android framework Service invocations and enables unit testing.
 */
interface SleepTrackingController {
    val isServiceRunning: StateFlow<Boolean>
    val isTrackingInterrupted: StateFlow<Boolean>
    val currentSessionId: StateFlow<String?>

    fun startTracking(sessionId: String, startTime: Instant = Instant.now(), notes: String? = null)
    fun stopTracking(notes: String? = null)
    fun discardTracking()
}

/**
 * Default Android implementation that delegates to [SleepTrackingService].
 */
class AndroidSleepTrackingController(
    private val context: Context
) : SleepTrackingController {

    override val isServiceRunning: StateFlow<Boolean>
        get() = SleepTrackingService.isServiceRunning

    override val isTrackingInterrupted: StateFlow<Boolean>
        get() = SleepTrackingService.isTrackingInterrupted

    override val currentSessionId: StateFlow<String?>
        get() = SleepTrackingService.currentSessionId

    override fun startTracking(sessionId: String, startTime: Instant, notes: String?) {
        SleepTrackingService.start(context, sessionId, startTime, notes)
    }

    override fun stopTracking(notes: String?) {
        SleepTrackingService.stop(context, notes, isFromAppUi = true)
    }

    override fun discardTracking() {
        SleepTrackingService.discard(context)
    }
}
