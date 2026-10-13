package com.example.somnus.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.somnus.data.repository.SleepSessionRepository
import com.example.somnus.domain.analysis.SessionValidator
import com.example.somnus.domain.analysis.ValidationResult
import com.example.somnus.domain.model.SleepSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

data class SessionDetailUiState(
    val session: SleepSession? = null,
    val isLoading: Boolean = false,
    val isDeleted: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

class SessionDetailViewModel(
    private val sessionId: String,
    private val repository: SleepSessionRepository
) : ViewModel() {

    private val _mutationState = MutableStateFlow(SessionDetailUiState(isLoading = true))

    val uiState: StateFlow<SessionDetailUiState> = combine(
        repository.getSessionById(sessionId),
        _mutationState
    ) { session, mutation ->
        if (mutation.isDeleted) {
            mutation
        } else {
            mutation.copy(
                session = session,
                isLoading = false
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SessionDetailUiState(isLoading = true)
    )

    fun correctSession(
        newStartTime: Instant,
        newEndTime: Instant?,
        newNotes: String?
    ) {
        viewModelScope.launch {
            val currentSession = repository.getSessionByIdOneShot(sessionId)
                ?: uiState.value.session
                ?: return@launch

            val validation = SessionValidator.validate(
                start = newStartTime,
                end = newEndTime,
                status = currentSession.status
            )

            if (validation is ValidationResult.Invalid) {
                _mutationState.value = _mutationState.value.copy(
                    errorMessage = validation.reason
                )
                return@launch
            }

            _mutationState.value = _mutationState.value.copy(isLoading = true, errorMessage = null)
            val updated = currentSession.copy(
                startTime = newStartTime,
                endTime = newEndTime,
                notes = newNotes,
                isCorrected = true,
                updatedAt = Instant.now()
            )
            val result = repository.updateSession(updated)
            result.onSuccess {
                _mutationState.value = _mutationState.value.copy(
                    isLoading = false,
                    isSaved = true,
                    errorMessage = null
                )
            }.onFailure { error ->
                _mutationState.value = _mutationState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Failed to save correction"
                )
            }
        }
    }

    fun deleteSession() {
        viewModelScope.launch {
            _mutationState.value = _mutationState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.deleteSession(sessionId)
            result.onSuccess {
                _mutationState.value = _mutationState.value.copy(
                    isLoading = false,
                    isDeleted = true,
                    errorMessage = null
                )
            }.onFailure { error ->
                _mutationState.value = _mutationState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Failed to delete session"
                )
            }
        }
    }

    fun clearError() {
        _mutationState.value = _mutationState.value.copy(errorMessage = null)
    }

    companion object {
        fun provideFactory(
            sessionId: String,
            repository: SleepSessionRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SessionDetailViewModel(sessionId, repository) as T
            }
        }
    }
}
