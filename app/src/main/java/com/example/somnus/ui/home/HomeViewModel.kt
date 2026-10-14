package com.example.somnus.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.somnus.data.repository.SleepSessionRepository
import com.example.somnus.domain.model.SleepSession
import com.example.somnus.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val activeSession: SleepSession? = null,
    val recentSessions: List<SleepSession> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showEndConfirmation: Boolean = false,
    val isSessionStale: Boolean = false
)

class HomeViewModel(
    private val sessionManager: SessionManager,
    private val repository: SleepSessionRepository
) : ViewModel() {

    private val _uiStateInternal = MutableStateFlow(HomeUiState(isLoading = true))

    val uiState: StateFlow<HomeUiState> = combine(
        sessionManager.activeSession,
        repository.completedSessions,
        _uiStateInternal
    ) { activeSession, completed, internalState ->
        internalState.copy(
            activeSession = activeSession,
            recentSessions = completed.take(3),
            isLoading = false,
            isSessionStale = sessionManager.isCurrentSessionStale()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(isLoading = true)
    )

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
                _uiStateInternal.value = _uiStateInternal.value.copy(isLoading = false)
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

    fun recoverStaleSession(correctedEndTime: java.time.Instant, notes: String? = null) {
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

    fun clearError() {
        _uiStateInternal.value = _uiStateInternal.value.copy(errorMessage = null)
    }

    companion object {
        fun provideFactory(
            sessionManager: SessionManager,
            repository: SleepSessionRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(sessionManager, repository) as T
            }
        }
    }
}
