package com.example.somnus.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "somnus_user_preferences")

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val policyAcknowledged: Boolean = false,
    val dailyStepGoal: Int = 8000,
    val phoneStepTrackingEnabled: Boolean = true
)

class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val POLICY_ACKNOWLEDGED = booleanPreferencesKey("policy_acknowledged")
        val DAILY_STEP_GOAL = intPreferencesKey("daily_step_goal")
        val PHONE_STEP_TRACKING_ENABLED = booleanPreferencesKey("phone_step_tracking_enabled")
    }

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data.map { preferences ->
        val themeModeName = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.DARK.name
        val themeMode = try {
            ThemeMode.valueOf(themeModeName)
        } catch (e: Exception) {
            ThemeMode.DARK
        }
        val acknowledged = preferences[PreferencesKeys.POLICY_ACKNOWLEDGED] ?: false
        val dailyStepGoal = preferences[PreferencesKeys.DAILY_STEP_GOAL] ?: 8000
        val phoneStepTrackingEnabled = preferences[PreferencesKeys.PHONE_STEP_TRACKING_ENABLED] ?: true
        UserPreferences(
            themeMode = themeMode,
            policyAcknowledged = acknowledged,
            dailyStepGoal = dailyStepGoal,
            phoneStepTrackingEnabled = phoneStepTrackingEnabled
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    suspend fun setPolicyAcknowledged(acknowledged: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.POLICY_ACKNOWLEDGED] = acknowledged
        }
    }

    suspend fun setDailyStepGoal(goal: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DAILY_STEP_GOAL] = goal.coerceIn(1000, 100000)
        }
    }

    suspend fun setPhoneStepTrackingEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PHONE_STEP_TRACKING_ENABLED] = enabled
        }
    }
}
