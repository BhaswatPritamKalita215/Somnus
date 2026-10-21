package com.example.somnus.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.somnus.data.healthconnect.HealthConnectManager
import com.example.somnus.data.repository.DashboardRepository
import com.example.somnus.data.repository.SleepSessionRepository
import com.example.somnus.domain.dashboard.CrossMetricInsight
import com.example.somnus.domain.dashboard.DashboardDayData
import com.example.somnus.domain.dashboard.DataCoverageSummary
import com.example.somnus.domain.model.SleepSession
import com.example.somnus.session.SessionManager
import com.example.somnus.session.SleepTrackingController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HomeUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val dayData: DashboardDayData = DashboardDayData(selectedDate = LocalDate.now()),
    val crossMetricInsights: List<CrossMetricInsight> = emptyList(),
    val coverageSummary: DataCoverageSummary? = null,
    val activeSession: SleepSession? = null,
    val recentSessions: List<SleepSession> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val feedbackMessage: String? = null,
    val showEndConfirmation: Boolean = false,
    val isSessionStale: Boolean = false,
    val isSyncing: Boolean = false,
    val isTrackingServiceRunning: Boolean = false,
    val isTrackingInterrupted: Boolean = false,
    val isStartingTracking: Boolean = false,
    val pendingCheckInSessionId: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val sessionManager: SessionManager,
    private val repository: SleepSessionRepository,
    private val dashboardRepository: DashboardRepository? = null,
    private val healthConnectManager: HealthConnectManager? = null,
    private val sleepTrackingController: SleepTrackingController? = null,
    private val sleepCheckInRepository: com.example.somnus.data.repository.SleepCheckInRepository? = null
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _uiStateInternal = MutableStateFlow(HomeUiState(isLoading = true))

    private val dayDataFlow = _selectedDate.flatMapLatest { date ->
        dashboardRepository?.observeDashboardDay(date) ?: flowOf(DashboardDayData(selectedDate = date))
    }

    private val insightsFlow = _selectedDate.flatMapLatest { date ->
        val start = date.minusDays(13)
        dashboardRepository?.observeCrossMetricInsights(start, date) ?: flowOf(emptyList())
    }

    private val coverageFlow = _selectedDate.flatMapLatest { date ->
        val start = date.minusDays(6)
        dashboardRepository?.observeDataCoverage(start, date) ?: flowOf(null)
    }

    private val serviceRunningFlow = sleepTrackingController?.isServiceRunning ?: flowOf(false)
    private val serviceInterruptedFlow = sleepTrackingController?.isTrackingInterrupted ?: flowOf(false)

    val uiState: StateFlow<HomeUiState> = combine(
        sessionManager.activeSession,
        repository.completedSessions,
        _selectedDate,
        dayDataFlow,
        insightsFlow,
        coverageFlow,
        serviceRunningFlow,
        serviceInterruptedFlow,
        _uiStateInternal
    ) { values ->
        val activeSession = values[0] as SleepSession?
        @Suppress("UNCHECKED_CAST")
        val completed = values[1] as List<SleepSession>
        val date = values[2] as LocalDate
        val dayData = values[3] as DashboardDayData
        @Suppress("UNCHECKED_CAST")
        val insights = values[4] as List<CrossMetricInsight>
        val coverage = values[5] as DataCoverageSummary?
        val serviceRunning = values[6] as Boolean
        val serviceInterrupted = values[7] as Boolean
        val internalState = values[8] as HomeUiState

        val hasActive = activeSession != null && activeSession.isActive
        val isInterrupted = hasActive && ((!serviceRunning && !internalState.isStartingTracking) || serviceInterrupted)

        internalState.copy(
            selectedDate = date,
            dayData = dayData.copy(activeTrackingSession = activeSession),
            crossMetricInsights = insights,
            coverageSummary = coverage,
            activeSession = activeSession,
            recentSessions = completed.take(5),
            isLoading = false,
            isSessionStale = sessionManager.isCurrentSessionStale(),
            isTrackingServiceRunning = serviceRunning,
            isTrackingInterrupted = isInterrupted
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(isLoading = true)
    )

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun stepDate(days: Long) {
        _selectedDate.value = _selectedDate.value.plusDays(days)
    }

    fun selectToday() {
        _selectedDate.value = LocalDate.now()
    }

    fun startSession(notes: String? = null) {
        viewModelScope.launch {
            _uiStateInternal.value = _uiStateInternal.value.copy(
                isLoading = true,
                errorMessage = null,
                isStartingTracking = true
            )
            val result = sessionManager.startSession(notes)
            result.onFailure { error ->
                _uiStateInternal.value = _uiStateInternal.value.copy(
                    isLoading = false,
                    isStartingTracking = false,
                    errorMessage = error.message ?: "Failed to start sleep session"
                )
            }.onSuccess { session ->
                _uiStateInternal.value = _uiStateInternal.value.copy(isLoading = false)
                try {
                    sleepTrackingController?.startTracking(session.id, session.startTime, notes)
                } catch (e: Exception) {
                    _uiStateInternal.value = _uiStateInternal.value.copy(
                        isStartingTracking = false,
                        errorMessage = "Sleep session created, but background service could not start: ${e.message}"
                    )
                }
            }
        }
    }

    fun resumeTracking() {
        val active = sessionManager.activeSession.value ?: uiState.value.activeSession ?: return
        try {
            sleepTrackingController?.startTracking(active.id, active.startTime, active.notes)
        } catch (e: Exception) {
            _uiStateInternal.value = _uiStateInternal.value.copy(
                errorMessage = "Failed to resume tracking: ${e.message}"
            )
        }
    }

    fun promptEndSession() {
        _uiStateInternal.value = _uiStateInternal.value.copy(showEndConfirmation = true)
    }

    fun dismissEndSession() {
        _uiStateInternal.value = _uiStateInternal.value.copy(showEndConfirmation = false)
    }

    fun confirmEndSession(notes: String? = null) {
        viewModelScope.launch {
            _uiStateInternal.value = _uiStateInternal.value.copy(
                isLoading = true,
                showEndConfirmation = false,
                errorMessage = null
            )
            sleepTrackingController?.stopTracking(notes)
            val result = sessionManager.endSession(notes)
            result.onFailure { error ->
                _uiStateInternal.value = _uiStateInternal.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Failed to end sleep session"
                )
            }.onSuccess { session ->
                _uiStateInternal.value = _uiStateInternal.value.copy(
                    isLoading = false,
                    pendingCheckInSessionId = session.id,
                    feedbackMessage = "Sleep session completed and recorded."
                )
            }
        }
    }

    fun discardActiveSession() {
        viewModelScope.launch {
            _uiStateInternal.value = _uiStateInternal.value.copy(
                isLoading = true,
                showEndConfirmation = false,
                errorMessage = null
            )
            sleepTrackingController?.discardTracking()
            val result = sessionManager.discardActiveSession()
            result.onFailure { error ->
                _uiStateInternal.value = _uiStateInternal.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Failed to discard session"
                )
            }.onSuccess {
                _uiStateInternal.value = _uiStateInternal.value.copy(isLoading = false)
            }
        }
    }

    fun recoverStaleSession(correctedEndTime: Instant, notes: String? = null) {
        viewModelScope.launch {
            _uiStateInternal.value = _uiStateInternal.value.copy(
                isLoading = true,
                showEndConfirmation = false,
                errorMessage = null
            )
            sleepTrackingController?.stopTracking(notes)
            val result = sessionManager.recoverActiveSession(correctedEndTime, notes)
            result.onFailure { error ->
                _uiStateInternal.value = _uiStateInternal.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Failed to recover session"
                )
            }.onSuccess {
                _uiStateInternal.value = _uiStateInternal.value.copy(
                    isLoading = false,
                    feedbackMessage = "Stale session recovered."
                )
            }
        }
    }

    fun syncHealthConnect(onPermissionRequired: ((Set<String>) -> Unit)? = null) {
        if (healthConnectManager == null) return
        viewModelScope.launch {
            val availability = healthConnectManager.checkAvailability()
            if (availability != com.example.somnus.data.healthconnect.HealthConnectAvailability.AVAILABLE) {
                _uiStateInternal.value = _uiStateInternal.value.copy(
                    isSyncing = false,
                    feedbackMessage = "Health Connect: ${availability.description}"
                )
                return@launch
            }

            val hasSleepPerms = healthConnectManager.hasPermissions()
            val allRequired = com.example.somnus.data.healthconnect.DefaultHealthConnectClientWrapper.REQUIRED_PERMISSIONS +
                com.example.somnus.data.healthconnect.DefaultHealthConnectClientWrapper.STEP_PERMISSIONS
            if (!hasSleepPerms && onPermissionRequired != null) {
                onPermissionRequired(allRequired)
                return@launch
            }

            _uiStateInternal.value = _uiStateInternal.value.copy(isSyncing = true)
            val zone = ZoneId.systemDefault()
            val start = _selectedDate.value.minusDays(7).atStartOfDay(zone).toInstant()
            val end = _selectedDate.value.plusDays(1).atStartOfDay(zone).toInstant()
            val sleepResult = healthConnectManager.syncSleepRecords(start, end)
            val stepResult = healthConnectManager.syncStepRecords(start, end)

            val error = sleepResult.exceptionOrNull() ?: stepResult.exceptionOrNull()
            if (error != null && sleepResult.isFailure && stepResult.isFailure) {
                _uiStateInternal.value = _uiStateInternal.value.copy(
                    isSyncing = false,
                    errorMessage = "Health Connect sync failed: ${error.message}"
                )
            } else {
                val sleepCount = sleepResult.getOrNull()?.importedCount ?: 0
                val stepCount = stepResult.getOrNull()?.importedCount ?: 0
                _uiStateInternal.value = _uiStateInternal.value.copy(
                    isSyncing = false,
                    feedbackMessage = "Health Connect synced: $sleepCount sleep records, $stepCount step records."
                )
            }
        }
    }

    fun saveMorningCheckIn(
        sessionId: String,
        moodScore: Int?,
        moodLabel: String?,
        restedScore: Int?,
        notes: String?
    ) {
        viewModelScope.launch {
            if (moodScore != null || restedScore != null || notes != null) {
                val checkIn = com.example.somnus.domain.model.SleepCheckIn(
                    sessionId = sessionId,
                    moodScore = moodScore,
                    moodLabel = moodLabel,
                    restedScore = restedScore,
                    notes = notes,
                    isSelfReported = true
                )
                sleepCheckInRepository?.saveCheckIn(checkIn)
            }
            _uiStateInternal.value = _uiStateInternal.value.copy(
                pendingCheckInSessionId = null,
                feedbackMessage = "Morning check-in saved."
            )
        }
    }

    fun skipMorningCheckIn() {
        _uiStateInternal.value = _uiStateInternal.value.copy(
            pendingCheckInSessionId = null
        )
    }

    fun clearError() {
        _uiStateInternal.value = _uiStateInternal.value.copy(errorMessage = null)
    }

    fun dismissFeedback() {
        _uiStateInternal.value = _uiStateInternal.value.copy(feedbackMessage = null)
    }

    companion object {
        fun provideFactory(
            sessionManager: SessionManager,
            repository: SleepSessionRepository,
            dashboardRepository: DashboardRepository? = null,
            healthConnectManager: HealthConnectManager? = null,
            sleepTrackingController: SleepTrackingController? = null,
            sleepCheckInRepository: com.example.somnus.data.repository.SleepCheckInRepository? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    sessionManager = sessionManager,
                    repository = repository,
                    dashboardRepository = dashboardRepository,
                    healthConnectManager = healthConnectManager,
                    sleepTrackingController = sleepTrackingController,
                    sleepCheckInRepository = sleepCheckInRepository
                ) as T
            }
        }
    }
}
