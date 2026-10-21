package com.example.somnus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.somnus.SomnusApplication
import com.example.somnus.data.preferences.ThemeMode
import com.example.somnus.theme.SomnusTheme
import com.example.somnus.ui.activity.ActivityScreen
import com.example.somnus.ui.activity.ActivityViewModel
import com.example.somnus.ui.analytics.AnalyticsViewModel
import com.example.somnus.ui.assistant.AssistantScreen
import com.example.somnus.ui.assistant.AssistantViewModel
import com.example.somnus.ui.detail.SessionDetailScreen
import com.example.somnus.ui.detail.SessionDetailViewModel
import com.example.somnus.ui.history.HistoryViewModel
import com.example.somnus.ui.home.HomeScreen
import com.example.somnus.ui.home.HomeViewModel
import com.example.somnus.ui.insights.InsightsViewModel
import com.example.somnus.ui.nutrition.NutritionScreen
import com.example.somnus.ui.nutrition.NutritionViewModel
import com.example.somnus.ui.settings.SettingsScreen
import com.example.somnus.ui.settings.SettingsViewModel
import com.example.somnus.ui.sleep.SleepHubScreen

sealed interface Screen {
    data object Track : Screen
    data object Sleep : Screen
    data object Nutrition : Screen
    data object Assistant : Screen
    data object Activity : Screen
    data object Settings : Screen
    data class Detail(val sessionId: String) : Screen
}

@Composable
fun SomnusApp() {
    val context = LocalContext.current
    val app = context.applicationContext as SomnusApplication
    val container = app.container

    // Collect user theme preference from DataStore
    val userPrefs by container.userPreferencesRepository.userPreferencesFlow
        .collectAsStateWithLifecycle(initialValue = com.example.somnus.data.preferences.UserPreferences())

    val isDark = when (userPrefs.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    SomnusTheme(darkTheme = isDark) {
        // Navigation BackStack
        val backStack = remember { mutableStateListOf<Screen>(Screen.Track) }
        val currentScreen = backStack.lastOrNull() ?: Screen.Track

        // Handle system back navigation
        BackHandler(enabled = backStack.size > 1) {
            backStack.removeLastOrNull()
        }

        fun navigateTo(screen: Screen) {
            if (screen == Screen.Track || screen == Screen.Sleep || screen == Screen.Nutrition || screen == Screen.Assistant) {
                // Top-level primary tab switch: clear backstack to root
                backStack.clear()
                backStack.add(screen)
            } else {
                backStack.add(screen)
            }
        }

        Scaffold(
            bottomBar = {
                // Show 4-item bottom navigation on primary screens
                if (currentScreen is Screen.Track || currentScreen is Screen.Sleep || currentScreen is Screen.Nutrition || currentScreen is Screen.Assistant) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentScreen is Screen.Track,
                            onClick = { navigateTo(Screen.Track) },
                            icon = { Icon(Icons.Default.Bedtime, contentDescription = "Home") },
                            label = { Text("Home") }
                        )
                        NavigationBarItem(
                            selected = currentScreen is Screen.Sleep,
                            onClick = { navigateTo(Screen.Sleep) },
                            icon = { Icon(Icons.Default.Hotel, contentDescription = "Sleep") },
                            label = { Text("Sleep") }
                        )
                        NavigationBarItem(
                            selected = currentScreen is Screen.Nutrition,
                            onClick = { navigateTo(Screen.Nutrition) },
                            icon = { Icon(Icons.Default.Restaurant, contentDescription = "Food") },
                            label = { Text("Food") }
                        )
                        NavigationBarItem(
                            selected = currentScreen is Screen.Assistant,
                            onClick = { navigateTo(Screen.Assistant) },
                            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Assistant") },
                            label = { Text("Assistant") }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentScreen) {
                    is Screen.Track -> {
                        val localContext = LocalContext.current
                        val homeViewModel: HomeViewModel = viewModel(
                            factory = HomeViewModel.provideFactory(
                                sessionManager = container.sessionManager,
                                repository = container.sleepSessionRepository,
                                dashboardRepository = container.dashboardRepository,
                                healthConnectManager = container.healthConnectManager,
                                sleepTrackingController = remember(localContext) {
                                    com.example.somnus.session.AndroidSleepTrackingController(localContext.applicationContext)
                                },
                                sleepCheckInRepository = container.sleepCheckInRepository
                            )
                        )
                        HomeScreen(
                            viewModel = homeViewModel,
                            onNavigateToHistory = { navigateTo(Screen.Sleep) },
                            onNavigateToDetail = { sessionId -> navigateTo(Screen.Detail(sessionId)) },
                            onNavigateToActivity = { navigateTo(Screen.Activity) },
                            onNavigateToNutrition = { navigateTo(Screen.Nutrition) },
                            onNavigateToAnalytics = { navigateTo(Screen.Sleep) },
                            onNavigateToAssistant = { navigateTo(Screen.Assistant) },
                            onNavigateToSettings = { navigateTo(Screen.Settings) }
                        )
                    }

                    is Screen.Sleep -> {
                        val historyViewModel: HistoryViewModel = viewModel(
                            factory = HistoryViewModel.provideFactory(
                                repository = container.sleepSessionRepository
                            )
                        )
                        val analyticsViewModel: AnalyticsViewModel = viewModel(
                            factory = AnalyticsViewModel.provideFactory(
                                analyticsRepository = container.sleepAnalyticsRepository
                            )
                        )
                        val insightsViewModel: InsightsViewModel = viewModel(
                            factory = InsightsViewModel.provideFactory(
                                solRepository = container.solRepository,
                                psqiRepository = container.psqiRepository
                            )
                        )
                        SleepHubScreen(
                            historyViewModel = historyViewModel,
                            analyticsViewModel = analyticsViewModel,
                            insightsViewModel = insightsViewModel,
                            onNavigateToDetail = { sessionId -> navigateTo(Screen.Detail(sessionId)) },
                            onNavigateToSettings = { navigateTo(Screen.Settings) }
                        )
                    }

                    is Screen.Activity -> {
                        val activityViewModel: ActivityViewModel = viewModel(
                            factory = ActivityViewModel.provideFactory(
                                stepRepository = container.stepRepository,
                                phoneStepTracker = container.phoneStepTracker,
                                healthConnectManager = container.healthConnectManager,
                                userPreferencesRepository = container.userPreferencesRepository
                            )
                        )
                        ActivityScreen(
                            viewModel = activityViewModel,
                            onNavigateToSettings = { navigateTo(Screen.Settings) }
                        )
                    }

                    is Screen.Nutrition -> {
                        val nutritionViewModel: NutritionViewModel = viewModel(
                            factory = NutritionViewModel.provideFactory(
                                foodProductRepository = container.foodProductRepository,
                                foodLogRepository = container.foodLogRepository,
                                userPreferencesRepository = container.userPreferencesRepository,
                                healthConnectManager = container.healthConnectManager
                            )
                        )
                        NutritionScreen(
                            viewModel = nutritionViewModel,
                            onNavigateToSettings = { navigateTo(Screen.Settings) }
                        )
                    }

                    is Screen.Settings -> {
                        val settingsViewModel: SettingsViewModel = viewModel(
                            factory = SettingsViewModel.provideFactory(
                                sessionRepository = container.sleepSessionRepository,
                                solRepository = container.solRepository,
                                psqiRepository = container.psqiRepository,
                                preferencesRepository = container.userPreferencesRepository,
                                database = container.database
                            )
                        )
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onNavigateBack = { backStack.removeLastOrNull() }
                        )
                    }

                    is Screen.Detail -> {
                        val detailViewModel: SessionDetailViewModel = viewModel(
                            key = currentScreen.sessionId,
                            factory = SessionDetailViewModel.provideFactory(
                                sessionId = currentScreen.sessionId,
                                repository = container.sleepSessionRepository
                            )
                        )
                        SessionDetailScreen(
                            viewModel = detailViewModel,
                            onNavigateBack = { backStack.removeLastOrNull() }
                        )
                    }

                    is Screen.Assistant -> {
                        val assistantViewModel: AssistantViewModel = viewModel(
                            factory = AssistantViewModel.provideFactory(
                                assistantRepository = container.assistantRepository
                            )
                        )
                        AssistantScreen(
                            viewModel = assistantViewModel,
                            onNavigateBack = {
                                if (backStack.size > 1) {
                                    backStack.removeLastOrNull()
                                } else {
                                    navigateTo(Screen.Track)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
