package com.example.somnus.ui.sleep

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.somnus.ui.analytics.AnalyticsScreen
import com.example.somnus.ui.analytics.AnalyticsViewModel
import com.example.somnus.ui.components.SomnusTopBar
import com.example.somnus.ui.history.HistoryScreen
import com.example.somnus.ui.history.HistoryViewModel
import com.example.somnus.ui.insights.InsightsScreen
import com.example.somnus.ui.insights.InsightsViewModel

/**
 * Unified Sleep & Health Hub merging History, Analytics (Trends), and Insights into
 * a single primary navigation destination with a secondary tab row.
 */
@Composable
fun SleepHubScreen(
    historyViewModel: HistoryViewModel,
    analyticsViewModel: AnalyticsViewModel,
    insightsViewModel: InsightsViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            SomnusTopBar(
                title = "Sleep & Health",
                subtitle = when (selectedTab) {
                    0 -> "Chronological History"
                    1 -> "Aggregate Trends"
                    else -> "Validated Insights"
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("History") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Trends") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Insights") }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> {
                        HistoryScreen(
                            viewModel = historyViewModel,
                            onNavigateToDetail = onNavigateToDetail,
                            showTopBar = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    1 -> {
                        AnalyticsScreen(
                            viewModel = analyticsViewModel,
                            showTopBar = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    2 -> {
                        InsightsScreen(
                            viewModel = insightsViewModel,
                            showTopBar = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
