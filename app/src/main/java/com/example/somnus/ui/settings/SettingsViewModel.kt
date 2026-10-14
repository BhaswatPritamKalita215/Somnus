package com.example.somnus.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.somnus.data.preferences.ThemeMode
import com.example.somnus.data.preferences.UserPreferences
import com.example.somnus.data.preferences.UserPreferencesRepository
import com.example.somnus.data.repository.PsqiRepository
import com.example.somnus.data.repository.SleepSessionRepository
import com.example.somnus.data.repository.SolRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val sessionCount: Int = 0,
    val solRecordCount: Int = 0,
    val psqiAssessmentCount: Int = 0,
    val databaseVersion: Int = 2,
    val appVersion: String = "1.1.0 (SOL & PSQI)",
    val userPreferences: UserPreferences = UserPreferences(),
    val isDeleting: Boolean = false,
    val deletionSuccess: Boolean = false,
    val errorMessage: String? = null
)

class SettingsViewModel(
    private val sessionRepository: SleepSessionRepository,
    private val solRepository: SolRepository,
    private val psqiRepository: PsqiRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _stateInternal = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(
        sessionRepository.allSessions,
        solRepository.allRecords,
        psqiRepository.allAssessments,
        preferencesRepository.userPreferencesFlow,
        _stateInternal
    ) { allSessions, solRecords, psqiAssessments, prefs, internalState ->
        internalState.copy(
            sessionCount = allSessions.size,
            solRecordCount = solRecords.size,
            psqiAssessmentCount = psqiAssessments.size,
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
            val sessionRes = sessionRepository.deleteAllSessions()
            val solRes = solRepository.deleteAll()
            val psqiRes = psqiRepository.deleteAll()

            if (sessionRes.isSuccess && solRes.isSuccess && psqiRes.isSuccess) {
                _stateInternal.value = _stateInternal.value.copy(
                    isDeleting = false,
                    deletionSuccess = true
                )
            } else {
                val errorMsg = sessionRes.exceptionOrNull()?.message
                    ?: solRes.exceptionOrNull()?.message
                    ?: psqiRes.exceptionOrNull()?.message
                    ?: "Failed to delete data"
                _stateInternal.value = _stateInternal.value.copy(
                    isDeleting = false,
                    errorMessage = errorMsg
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
            sessionRepository: SleepSessionRepository,
            solRepository: SolRepository,
            psqiRepository: PsqiRepository,
            preferencesRepository: UserPreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    sessionRepository = sessionRepository,
                    solRepository = solRepository,
                    psqiRepository = psqiRepository,
                    preferencesRepository = preferencesRepository
                ) as T
            }
        }
    }
}
