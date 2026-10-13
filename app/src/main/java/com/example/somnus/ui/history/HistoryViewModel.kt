package com.example.somnus.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.somnus.data.repository.SleepSessionRepository
import com.example.somnus.domain.model.SleepSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Duration

data class HistoryUiState(
    val sessions: List<SleepSession> = emptyList(),
    val totalTimeInBed: Duration = Duration.ZERO,
    val averageTimeInBed: Duration = Duration.ZERO,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class HistoryViewModel(
    private val repository: SleepSessionRepository
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = repository.completedSessions
        .map { list ->
            val totalSeconds = list.mapNotNull { it.duration?.seconds }.sum()
            val totalDuration = Duration.ofSeconds(totalSeconds)
            val avgDuration = if (list.isNotEmpty()) {
                Duration.ofSeconds(totalSeconds / list.size)
            } else {
                Duration.ZERO
            }
            HistoryUiState(
                sessions = list,
                totalTimeInBed = totalDuration,
                averageTimeInBed = avgDuration,
                isLoading = false
            )
        }
        .catch { error ->
            emit(HistoryUiState(isLoading = false, errorMessage = error.message))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = HistoryUiState(isLoading = true)
        )

    companion object {
        fun provideFactory(
            repository: SleepSessionRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HistoryViewModel(repository) as T
            }
        }
    }
}
