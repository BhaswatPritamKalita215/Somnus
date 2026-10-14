package com.example.somnus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
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
import com.example.somnus.ui.analytics.AnalyticsScreen
import com.example.somnus.ui.analytics.AnalyticsViewModel
import com.example.somnus.ui.detail.SessionDetailScreen
import com.example.somnus.ui.detail.SessionDetailViewModel
import com.example.somnus.ui.history.HistoryScreen
import com.example.somnus.ui.history.HistoryViewModel
import com.example.somnus.ui.home.HomeScreen
import com.example.somnus.ui.home.HomeViewModel
import com.example.somnus.ui.insights.InsightsScreen
import com.example.somnus.ui.insights.InsightsViewModel
import com.example.somnus.ui.settings.SettingsScreen
import com.example.somnus.ui.settings.SettingsViewModel

sealed interface Screen {
    data object Track : Screen
    data object History : Screen
    data object Analytics : Screen
    data object Insights : Screen
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
            if (screen == Screen.Track || screen == Screen.History || screen == Screen.Analytics || screen == Screen.Insights || screen == Screen.Settings) {
                // Top-level tab switch: clear backstack to root
                backStack.clear()
                backStack.add(screen)
            } else {
                backStack.add(screen)
            }
        }

        Scaffold(
            bottomBar = {
                // Show bottom navigation on primary screens
                if (currentScreen !is Screen.Detail) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentScreen is Screen.Track,
                            onClick = { navigateTo(Screen.Track) },
                            icon = { Icon(Icons.Default.Bedtime, contentDescription = "Track") },
                            label = { Text("Track") }
                        )
                        NavigationBarItem(
                            selected = currentScreen is Screen.History,
                            onClick = { navigateTo(Screen.History) },
                            icon = { Icon(Icons.Default.History, contentDescription = "History") },
                            label = { Text("History") }
                        )
                        NavigationBarItem(
                            selected = currentScreen is Screen.Analytics,
                            onClick = { navigateTo(Screen.Analytics) },
                            icon = { Icon(Icons.Default.Analytics, contentDescription = "Analytics") },
                            label = { Text("Analytics") }
                        )
                        NavigationBarItem(
                            selected = currentScreen is Screen.Insights,
                            onClick = { navigateTo(Screen.Insights) },
                            icon = { Icon(Icons.Default.Insights, contentDescription = "Insights") },
                            label = { Text("Insights") }
                        )
                        NavigationBarItem(
                            selected = currentScreen is Screen.Settings,
                            onClick = { navigateTo(Screen.Settings) },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") }
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
                        val homeViewModel: HomeViewModel = viewModel(
                            factory = HomeViewModel.provideFactory(
                                sessionManager = container.sessionManager,
                                repository = container.sleepSessionRepository
                            )
                        )
                        HomeScreen(
                            viewModel = homeViewModel,
                            onNavigateToHistory = { navigateTo(Screen.History) },
                            onNavigateToDetail = { sessionId -> navigateTo(Screen.Detail(sessionId)) }
                        )
                    }

                    is Screen.History -> {
                        val historyViewModel: HistoryViewModel = viewModel(
                            factory = HistoryViewModel.provideFactory(
                                repository = container.sleepSessionRepository
                            )
                        )
                        HistoryScreen(
                            viewModel = historyViewModel,
                            onNavigateToDetail = { sessionId -> navigateTo(Screen.Detail(sessionId)) }
                        )
                    }

                    is Screen.Analytics -> {
                        val analyticsViewModel: AnalyticsViewModel = viewModel(
                            factory = AnalyticsViewModel.provideFactory(
                                analyticsRepository = container.sleepAnalyticsRepository
                            )
                        )
                        AnalyticsScreen(
                            viewModel = analyticsViewModel
                        )
                    }

                    is Screen.Insights -> {
                        val insightsViewModel: InsightsViewModel = viewModel(
                            factory = InsightsViewModel.provideFactory(
                                solRepository = container.solRepository,
                                psqiRepository = container.psqiRepository
                            )
                        )
                        InsightsScreen(
                            viewModel = insightsViewModel
                        )
                    }

                    is Screen.Settings -> {
                        val settingsViewModel: SettingsViewModel = viewModel(
                            factory = SettingsViewModel.provideFactory(
                                sessionRepository = container.sleepSessionRepository,
                                solRepository = container.solRepository,
                                psqiRepository = container.psqiRepository,
                                preferencesRepository = container.userPreferencesRepository
                            )
                        )
                        SettingsScreen(
                            viewModel = settingsViewModel
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
                }
            }
        }
    }
}
