package com.example.somnus.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.somnus.data.healthconnect.HealthConnectAvailability
import com.example.somnus.data.healthconnect.HealthConnectManager
import com.example.somnus.data.preferences.UserPreferencesRepository
import com.example.somnus.data.repository.StepRepository
import com.example.somnus.data.sensor.PhoneStepTracker
import com.example.somnus.data.sensor.PhoneStepTrackerStatus
import com.example.somnus.domain.model.DailyStepSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ActivityUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val todaySummary: DailyStepSummary = DailyStepSummary(
        date = LocalDate.now(),
        totalSteps = 0L,
        goalSteps = 8000
    ),
    val recentSummaries: List<DailyStepSummary> = emptyList(),
    val weeklyAverageSteps: Long = 0L,
    val daysGoalMet: Int = 0,
    val phoneTrackerStatus: PhoneStepTrackerStatus = PhoneStepTrackerStatus.READY,
    val isPhoneTrackingActive: Boolean = false,
    val isHealthConnectAvailable: Boolean = false,
    val hasHealthConnectPermissions: Boolean = false,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val showGoalDialog: Boolean = false,
    val currentGoalInput: String = "8000"
)

class ActivityViewModel(
    private val stepRepository: StepRepository,
    private val phoneStepTracker: PhoneStepTracker,
    private val healthConnectManager: HealthConnectManager,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    private val _syncMessage = MutableStateFlow<String?>(null)
    private val _showGoalDialog = MutableStateFlow(false)
    private val _goalInput = MutableStateFlow("8000")

    private val today = LocalDate.now()
    private val weekAgo = today.minusDays(6)

    val uiState: StateFlow<ActivityUiState> = combine(
        userPreferencesRepository.userPreferencesFlow,
        stepRepository.getDailySummary(today, 8000),
        stepRepository.getDailySummariesForRange(weekAgo, today, 8000),
        phoneStepTracker.status,
        phoneStepTracker.isTracking,
        _isSyncing,
        _syncMessage,
        _showGoalDialog,
        _goalInput
    ) { values ->
        val userPrefs = values[0] as com.example.somnus.data.preferences.UserPreferences
        val todaySummaryRaw = values[1] as DailyStepSummary
        @Suppress("UNCHECKED_CAST")
        val recentList = values[2] as List<DailyStepSummary>
        val trackerStatus = values[3] as PhoneStepTrackerStatus
        val isTracking = values[4] as Boolean
        val isSyncing = values[5] as Boolean
        val syncMessage = values[6] as String?
        val showDialog = values[7] as Boolean
        val goalInput = values[8] as String

        val goal = userPrefs.dailyStepGoal
        val todaySummary = todaySummaryRaw.copy(
            goalSteps = goal,
            isGoalReached = todaySummaryRaw.totalSteps >= goal,
            goalProgressPercentage = if (goal > 0) {
                ((todaySummaryRaw.totalSteps.toFloat() / goal.toFloat()) * 100f).coerceIn(0f, 1000f)
            } else 0f
        )

        val updatedRecent = recentList.map { summary ->
            summary.copy(
                goalSteps = goal,
                isGoalReached = summary.totalSteps >= goal,
                goalProgressPercentage = if (goal > 0) {
                    ((summary.totalSteps.toFloat() / goal.toFloat()) * 100f).coerceIn(0f, 1000f)
                } else 0f
            )
        }

        val weeklyTotal = updatedRecent.sumOf { it.totalSteps }
        val weeklyAvg = if (updatedRecent.isNotEmpty()) weeklyTotal / updatedRecent.size else 0L
        val goalMetCount = updatedRecent.count { it.isGoalReached }

        val isHcAvailable = healthConnectManager.checkAvailability() == HealthConnectAvailability.AVAILABLE

        ActivityUiState(
            selectedDate = today,
            todaySummary = todaySummary,
            recentSummaries = updatedRecent,
            weeklyAverageSteps = weeklyAvg,
            daysGoalMet = goalMetCount,
            phoneTrackerStatus = trackerStatus,
            isPhoneTrackingActive = isTracking,
            isHealthConnectAvailable = isHcAvailable,
            hasHealthConnectPermissions = false, // Updated asynchronously in refreshPermissions
            isSyncing = isSyncing,
            syncMessage = syncMessage,
            showGoalDialog = showDialog,
            currentGoalInput = goalInput
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ActivityUiState()
    )

    init {
        refreshHardwareTracker()
        checkHealthConnectPermissions()
    }

    fun refreshHardwareTracker() {
        val status = phoneStepTracker.checkStatus()
        if (status == PhoneStepTrackerStatus.READY && !phoneStepTracker.isTracking.value) {
            phoneStepTracker.startTracking()
        }
    }

    fun togglePhoneTracking(enable: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setPhoneStepTrackingEnabled(enable)
            if (enable) {
                phoneStepTracker.startTracking()
            } else {
                phoneStepTracker.stopTracking()
            }
        }
    }

    fun checkHealthConnectPermissions() {
        viewModelScope.launch {
            val hasPerms = healthConnectManager.hasStepPermissions()
            // We can refresh or update state if needed
        }
    }

    fun syncHealthConnect(onPermissionRequired: ((Set<String>) -> Unit)? = null) {
        viewModelScope.launch {
            val availability = healthConnectManager.checkAvailability()
            if (availability != com.example.somnus.data.healthconnect.HealthConnectAvailability.AVAILABLE) {
                _syncMessage.value = "Health Connect: ${availability.description}"
                return@launch
            }

            val hasStepPerms = healthConnectManager.hasStepPermissions()
            if (!hasStepPerms && onPermissionRequired != null) {
                onPermissionRequired(com.example.somnus.data.healthconnect.DefaultHealthConnectClientWrapper.STEP_PERMISSIONS)
                return@launch
            }

            _isSyncing.value = true
            _syncMessage.value = "Syncing step records from Health Connect..."
            val start = Instant.now().minus(7, ChronoUnit.DAYS)
            val end = Instant.now()
            val result = healthConnectManager.syncStepRecords(start, end)
            _isSyncing.value = false
            if (result.isSuccess) {
                val summary = result.getOrThrow()
                _syncMessage.value = summary.message
            } else {
                _syncMessage.value = "Health Connect sync failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun onOpenGoalDialog() {
        val currentGoal = uiState.value.todaySummary.goalSteps
        _goalInput.value = currentGoal.toString()
        _showGoalDialog.value = true
    }

    fun onDismissGoalDialog() {
        _showGoalDialog.value = false
    }

    fun onGoalInputChange(value: String) {
        _goalInput.value = value.filter { it.isDigit() }
    }

    fun onSaveGoal() {
        val newGoal = _goalInput.value.toIntOrNull() ?: 8000
        viewModelScope.launch {
            userPreferencesRepository.setDailyStepGoal(newGoal)
            _showGoalDialog.value = false
        }
    }

    companion object {
        fun provideFactory(
            stepRepository: StepRepository,
            phoneStepTracker: PhoneStepTracker,
            healthConnectManager: HealthConnectManager,
            userPreferencesRepository: UserPreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ActivityViewModel(
                    stepRepository = stepRepository,
                    phoneStepTracker = phoneStepTracker,
                    healthConnectManager = healthConnectManager,
                    userPreferencesRepository = userPreferencesRepository
                ) as T
            }
        }
    }
}
