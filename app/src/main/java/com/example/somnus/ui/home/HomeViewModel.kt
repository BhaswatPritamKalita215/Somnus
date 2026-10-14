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
    val isSyncing: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val sessionManager: SessionManager,
    private val repository: SleepSessionRepository,
    private val dashboardRepository: DashboardRepository? = null,
    private val healthConnectManager: HealthConnectManager? = null
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

    val uiState: StateFlow<HomeUiState> = combine(
        sessionManager.activeSession,
        repository.completedSessions,
        _selectedDate,
        dayDataFlow,
        insightsFlow,
        coverageFlow,
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
        val internalState = values[6] as HomeUiState

        internalState.copy(
            selectedDate = date,
            dayData = dayData.copy(activeTrackingSession = activeSession),
            crossMetricInsights = insights,
            coverageSummary = coverage,
            activeSession = activeSession,
            recentSessions = completed.take(5),
            isLoading = false,
            isSessionStale = sessionManager.isCurrentSessionStale()
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
            _uiStateInternal.value = _uiStateInternal.value.copy(isLoading = true, errorMessage = null)
            val result = sessionManager.startSession(notes)
            result.onFailure { error ->
                _uiStateInternal.value = _uiStateInternal.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Failed to start sleep session"
                )
            }.onSuccess {
                _uiStateInternal.value = _uiStateInternal.value.copy(isLoading = false)
            }
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
            val result = sessionManager.endSession(notes)
            result.onFailure { error ->
                _uiStateInternal.value = _uiStateInternal.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Failed to end sleep session"
                )
            }.onSuccess {
                _uiStateInternal.value = _uiStateInternal.value.copy(
                    isLoading = false,
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
            val result = sessionManager.recoverActiveSession(correctedEndTime, notes)
            result.onFailure { error ->
                _uiStateInternal.value = _uiStateInternal.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Failed to recover session"
                )
            }.onSuccess {
                _uiStateInternal.value = _uiStateInternal.value.copy(isLoading = false)
            }
        }
    }

    fun syncHealthConnect() {
        if (healthConnectManager == null) return
        viewModelScope.launch {
            _uiStateInternal.value = _uiStateInternal.value.copy(isSyncing = true)
            val zone = ZoneId.systemDefault()
            val start = _selectedDate.value.minusDays(7).atStartOfDay(zone).toInstant()
            val end = _selectedDate.value.plusDays(1).atStartOfDay(zone).toInstant()
            val sleepResult = healthConnectManager.syncSleepRecords(start, end)
            val stepResult = healthConnectManager.syncStepRecords(start, end)

            val sleepCount = sleepResult.getOrNull()?.importedCount ?: 0
            val stepCount = stepResult.getOrNull()?.importedCount ?: 0

            _uiStateInternal.value = _uiStateInternal.value.copy(
                isSyncing = false,
                feedbackMessage = "Health Connect synced: $sleepCount sleep records, $stepCount step records."
            )
        }
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
            healthConnectManager: HealthConnectManager? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    sessionManager = sessionManager,
                    repository = repository,
                    dashboardRepository = dashboardRepository,
                    healthConnectManager = healthConnectManager
                ) as T
            }
        }
    }
}
