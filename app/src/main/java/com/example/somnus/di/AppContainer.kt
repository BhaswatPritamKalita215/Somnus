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
import com.example.somnus.data.repository.StepRepository
import com.example.somnus.data.repository.StepRepositoryImpl
import com.example.somnus.data.sensor.PhoneStepTracker
import com.example.somnus.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    val stepRepository: StepRepository
    val phoneStepTracker: PhoneStepTracker
    val foodProductRepository: com.example.somnus.data.repository.FoodProductRepository
    val foodLogRepository: com.example.somnus.data.repository.FoodLogRepository
    val dashboardRepository: com.example.somnus.data.repository.DashboardRepository
    val assistantRepository: com.example.somnus.data.repository.AssistantRepository
    val sleepCheckInRepository: com.example.somnus.data.repository.SleepCheckInRepository
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

    override val stepRepository: StepRepository by lazy {
        StepRepositoryImpl(
            stepRecordDao = database.stepRecordDao(),
            stepCheckpointDao = database.stepCheckpointDao()
        )
    }

    override val healthConnectManager: com.example.somnus.data.healthconnect.HealthConnectManager by lazy {
        com.example.somnus.data.healthconnect.HealthConnectManager(
            clientWrapper = healthConnectClientWrapper,
            repository = sleepSessionRepository,
            stepRepository = stepRepository
        )
    }

    override val phoneSensorTracker: com.example.somnus.data.sensor.PhoneSensorTracker by lazy {
        com.example.somnus.data.sensor.PhoneSensorTracker(context)
    }

    override val phoneStepTracker: PhoneStepTracker by lazy {
        PhoneStepTracker(
            context = context,
            stepRepository = stepRepository,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )
    }

    override val foodProductRepository: com.example.somnus.data.repository.FoodProductRepository by lazy {
        com.example.somnus.data.repository.FoodProductRepositoryImpl(
            foodProductDao = database.foodProductDao(),
            remoteClient = com.example.somnus.data.network.OpenFoodFactsClient()
        )
    }

    override val foodLogRepository: com.example.somnus.data.repository.FoodLogRepository by lazy {
        com.example.somnus.data.repository.FoodLogRepositoryImpl(
            foodLogDao = database.foodLogDao()
        )
    }

    override val dashboardRepository: com.example.somnus.data.repository.DashboardRepository by lazy {
        com.example.somnus.data.repository.DashboardRepositoryImpl(
            sleepSessionRepository = sleepSessionRepository,
            solRepository = solRepository,
            psqiRepository = psqiRepository,
            stepRepository = stepRepository,
            foodLogRepository = foodLogRepository,
            userPreferencesRepository = userPreferencesRepository,
            sessionManager = sessionManager,
            healthConnectManager = healthConnectManager
        )
    }

    override val sleepCheckInRepository: com.example.somnus.data.repository.SleepCheckInRepository by lazy {
        com.example.somnus.data.repository.SleepCheckInRepositoryImpl(database.sleepCheckInDao())
    }

    val assistantCache: com.example.somnus.domain.assistant.AssistantCache by lazy {
        com.example.somnus.domain.assistant.AssistantCache()
    }

    val localDataQueryService: com.example.somnus.domain.assistant.LocalDataQueryService by lazy {
        com.example.somnus.domain.assistant.LocalDataQueryService(
            sleepSessionRepository = sleepSessionRepository,
            solRepository = solRepository,
            stepRepository = stepRepository,
            foodLogRepository = foodLogRepository,
            dashboardRepository = dashboardRepository,
            userPreferencesRepository = userPreferencesRepository,
            healthConnectManager = healthConnectManager
        )
    }

    val assistantContextBuilder: com.example.somnus.domain.assistant.ContextBuilder by lazy {
        com.example.somnus.domain.assistant.ContextBuilder(
            sleepSessionRepository = sleepSessionRepository,
            solRepository = solRepository,
            stepRepository = stepRepository,
            foodLogRepository = foodLogRepository,
            dashboardRepository = dashboardRepository,
            userPreferencesRepository = userPreferencesRepository
        )
    }

    val assistantModelProvider: com.example.somnus.domain.assistant.AssistantModelProvider by lazy {
        com.example.somnus.domain.assistant.GroqModelProvider(
            userPreferencesRepository = userPreferencesRepository,
            localFallback = com.example.somnus.domain.assistant.DeterministicLocalAssistantModelProvider(),
            isOnlineProvider = { com.example.somnus.domain.assistant.isDeviceOnline(context) }
        )
    }

    override val assistantRepository: com.example.somnus.data.repository.AssistantRepository by lazy {
        com.example.somnus.data.repository.AssistantRepositoryImpl(
            dao = database.assistantMessageDao(),
            localDataQueryService = localDataQueryService,
            contextBuilder = assistantContextBuilder,
            modelProvider = assistantModelProvider,
            assistantCache = assistantCache,
            sleepSessionRepository = sleepSessionRepository,
            stepRepository = stepRepository,
            foodLogRepository = foodLogRepository,
            externalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        )
    }
}
