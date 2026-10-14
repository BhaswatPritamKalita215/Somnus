package com.example.somnus.di

import android.content.Context
import com.example.somnus.data.local.SomnusDatabase
import com.example.somnus.data.preferences.UserPreferencesRepository
import com.example.somnus.data.preferences.dataStore
import com.example.somnus.data.repository.PsqiRepository
import com.example.somnus.data.repository.PsqiRepositoryImpl
import com.example.somnus.data.repository.SleepAnalyticsRepository
import com.example.somnus.data.repository.SleepAnalyticsRepositoryImpl
import com.example.somnus.data.repository.SleepSessionRepository
import com.example.somnus.data.repository.SleepSessionRepositoryImpl
import com.example.somnus.data.repository.SolRepository
import com.example.somnus.data.repository.SolRepositoryImpl
import com.example.somnus.session.SessionManager

/**
 * Clean dependency container providing constructor injection for repositories and managers.
 */
interface AppContainer {
    val database: SomnusDatabase
    val sleepSessionRepository: SleepSessionRepository
    val solRepository: SolRepository
    val psqiRepository: PsqiRepository
    val sleepAnalyticsRepository: SleepAnalyticsRepository
    val userPreferencesRepository: UserPreferencesRepository
    val sessionManager: SessionManager
    val healthConnectClientWrapper: com.example.somnus.data.healthconnect.HealthConnectClientWrapper
    val healthConnectManager: com.example.somnus.data.healthconnect.HealthConnectManager
    val phoneSensorTracker: com.example.somnus.data.sensor.PhoneSensorTracker
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val database: SomnusDatabase by lazy {
        SomnusDatabase.buildDatabase(context)
    }

    override val sleepSessionRepository: SleepSessionRepository by lazy {
        SleepSessionRepositoryImpl(database.sleepSessionDao())
    }

    override val solRepository: SolRepository by lazy {
        SolRepositoryImpl(database.solRecordDao())
    }

    override val psqiRepository: PsqiRepository by lazy {
        PsqiRepositoryImpl(database.psqiDao())
    }

    override val sleepAnalyticsRepository: SleepAnalyticsRepository by lazy {
        SleepAnalyticsRepositoryImpl(
            sessionRepository = sleepSessionRepository,
            solRepository = solRepository,
            psqiRepository = psqiRepository
        )
    }

    override val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(context.dataStore)
    }

    override val sessionManager: SessionManager by lazy {
        SessionManager(sleepSessionRepository)
    }

    override val healthConnectClientWrapper: com.example.somnus.data.healthconnect.HealthConnectClientWrapper by lazy {
        com.example.somnus.data.healthconnect.DefaultHealthConnectClientWrapper(context)
    }

    override val healthConnectManager: com.example.somnus.data.healthconnect.HealthConnectManager by lazy {
        com.example.somnus.data.healthconnect.HealthConnectManager(
            clientWrapper = healthConnectClientWrapper,
            repository = sleepSessionRepository
        )
    }

    override val phoneSensorTracker: com.example.somnus.data.sensor.PhoneSensorTracker by lazy {
        com.example.somnus.data.sensor.PhoneSensorTracker(context)
    }
}
