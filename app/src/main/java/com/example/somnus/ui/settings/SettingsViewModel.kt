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
    val databaseVersion: Int = 3,
    val appVersion: String = "1.3.0 (Milestone 6: Step Tracking & Activity)",
    val userPreferences: UserPreferences = UserPreferences(),
    val isDeleting: Boolean = false,
    val deletionSuccess: Boolean = false,
    val errorMessage: String? = null,
    val isTestingGroq: Boolean = false,
    val groqTestResult: String? = null,
    val groqTestSuccess: Boolean? = null
)

class SettingsViewModel(
    private val sessionRepository: SleepSessionRepository,
    private val solRepository: SolRepository,
    private val psqiRepository: PsqiRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val database: com.example.somnus.data.local.SomnusDatabase? = null,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
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

    fun setOnlineFoodLookupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setOnlineFoodLookupEnabled(enabled)
        }
    }

    fun setGroqApiKey(apiKey: String) {
        viewModelScope.launch {
            preferencesRepository.setGroqApiKey(apiKey)
        }
    }

    fun setGroqPrivacyConsent(granted: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setGroqPrivacyConsentGranted(granted)
        }
    }

    fun testGroqConnection(apiKey: String) {
        viewModelScope.launch {
            _stateInternal.value = _stateInternal.value.copy(
                isTestingGroq = true,
                groqTestResult = null,
                groqTestSuccess = null
            )
            val provider = com.example.somnus.domain.assistant.GroqModelProvider(
                apiKey = apiKey,
                ioDispatcher = ioDispatcher
            )
            val result = provider.testConnection(apiKey)
            if (result.isSuccess) {
                _stateInternal.value = _stateInternal.value.copy(
                    isTestingGroq = false,
                    groqTestResult = result.getOrNull(),
                    groqTestSuccess = true
                )
            } else {
                _stateInternal.value = _stateInternal.value.copy(
                    isTestingGroq = false,
                    groqTestResult = result.exceptionOrNull()?.message ?: "Connection test failed",
                    groqTestSuccess = false
                )
            }
        }
    }

    fun clearGroqTestResult() {
        _stateInternal.value = _stateInternal.value.copy(
            groqTestResult = null,
            groqTestSuccess = null
        )
    }

    fun deleteAllData() {
        viewModelScope.launch {
            _stateInternal.value = _stateInternal.value.copy(isDeleting = true, errorMessage = null)
            if (database != null) {
                try {
                    database.clearAllDataTables()
                    _stateInternal.value = _stateInternal.value.copy(
                        isDeleting = false,
                        deletionSuccess = true
                    )
                } catch (e: Exception) {
                    _stateInternal.value = _stateInternal.value.copy(
                        isDeleting = false,
                        errorMessage = e.message ?: "Failed to delete data atomically"
                    )
                }
            } else {
                // Delete child tables first to respect foreign keys
                val solRes = solRepository.deleteAll()
                val psqiRes = psqiRepository.deleteAll()
                val sessionRes = sessionRepository.deleteAllSessions()

                if (sessionRes.isSuccess && solRes.isSuccess && psqiRes.isSuccess) {
                    _stateInternal.value = _stateInternal.value.copy(
                        isDeleting = false,
                        deletionSuccess = true
                    )
                } else {
                    val errorMsg = solRes.exceptionOrNull()?.message
                        ?: psqiRes.exceptionOrNull()?.message
                        ?: sessionRes.exceptionOrNull()?.message
                        ?: "Failed to delete data"
                    _stateInternal.value = _stateInternal.value.copy(
                        isDeleting = false,
                        errorMessage = errorMsg
                    )
                }
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
            preferencesRepository: UserPreferencesRepository,
            database: com.example.somnus.data.local.SomnusDatabase? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    sessionRepository = sessionRepository,
                    solRepository = solRepository,
                    psqiRepository = psqiRepository,
                    preferencesRepository = preferencesRepository,
                    database = database
                ) as T
            }
        }
    }
}
