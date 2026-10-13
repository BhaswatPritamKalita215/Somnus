package com.example.somnus.di

import android.content.Context
import com.example.somnus.data.local.SomnusDatabase
import com.example.somnus.data.preferences.UserPreferencesRepository
import com.example.somnus.data.preferences.dataStore
import com.example.somnus.data.repository.SleepSessionRepository
import com.example.somnus.data.repository.SleepSessionRepositoryImpl
import com.example.somnus.session.SessionManager

/**
 * Clean dependency container providing constructor injection for repositories and managers.
 */
interface AppContainer {
    val database: SomnusDatabase
    val sleepSessionRepository: SleepSessionRepository
    val userPreferencesRepository: UserPreferencesRepository
    val sessionManager: SessionManager
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val database: SomnusDatabase by lazy {
        SomnusDatabase.buildDatabase(context)
    }

    override val sleepSessionRepository: SleepSessionRepository by lazy {
        SleepSessionRepositoryImpl(database.sleepSessionDao())
    }

    override val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(context.dataStore)
    }

    override val sessionManager: SessionManager by lazy {
        SessionManager(sleepSessionRepository)
    }
}
