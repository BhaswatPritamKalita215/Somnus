package com.example.somnus.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.somnus.data.preferences.ThemeMode
import com.example.somnus.data.preferences.UserPreferences
import com.example.somnus.data.preferences.UserPreferencesRepository
import com.example.somnus.data.repository.SleepSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val sessionCount: Int = 0,
    val databaseVersion: Int = 1,
    val appVersion: String = "1.0.0 (Milestone 1)",
    val userPreferences: UserPreferences = UserPreferences(),
    val isDeleting: Boolean = false,
    val deletionSuccess: Boolean = false,
    val errorMessage: String? = null
)

class SettingsViewModel(
    private val repository: SleepSessionRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _stateInternal = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.allSessions,
        preferencesRepository.userPreferencesFlow,
        _stateInternal
    ) { allSessions, prefs, internalState ->
        internalState.copy(
            sessionCount = allSessions.size,
            userPreferences = prefs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState()
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            _stateInternal.value = _stateInternal.value.copy(isDeleting = true, errorMessage = null)
            val result = repository.deleteAllSessions()
            result.onSuccess {
                _stateInternal.value = _stateInternal.value.copy(
                    isDeleting = false,
                    deletionSuccess = true
                )
            }.onFailure { error ->
                _stateInternal.value = _stateInternal.value.copy(
                    isDeleting = false,
                    errorMessage = error.message ?: "Failed to delete data"
                )
            }
        }
    }

    fun clearMessages() {
        _stateInternal.value = _stateInternal.value.copy(
            errorMessage = null,
            deletionSuccess = false
        )
    }

    companion object {
        fun provideFactory(
            repository: SleepSessionRepository,
            preferencesRepository: UserPreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(repository, preferencesRepository) as T
            }
        }
    }
}
