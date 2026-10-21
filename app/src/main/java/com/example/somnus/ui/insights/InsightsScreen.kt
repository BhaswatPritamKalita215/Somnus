package com.example.somnus.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.somnus.ui.components.SomnusTopBar

@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                SomnusTopBar(
                    title = "Sleep Insights",
                    subtitle = "SOL & PSQI Instruments"
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = state.selectedTab == InsightsTab.SOL,
                    onClick = { viewModel.selectTab(InsightsTab.SOL) },
                    text = { Text("Nightly SOL") }
                )
                Tab(
                    selected = state.selectedTab == InsightsTab.PSQI,
                    onClick = { viewModel.selectTab(InsightsTab.PSQI) },
                    text = { Text("Monthly PSQI") }
                )
                Tab(
                    selected = state.selectedTab == InsightsTab.COMPARISON,
                    onClick = { viewModel.selectTab(InsightsTab.COMPARISON) },
                    text = { Text("Combined") }
                )
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                when (state.selectedTab) {
                    InsightsTab.SOL -> {
                        SolSection(
                            uiState = state,
                            onDeleteRecord = { id -> viewModel.deleteSolRecord(id) }
                        )
                    }

                    InsightsTab.PSQI -> {
                        PsqiSection(
                            uiState = state,
                            onScoreAssessment = { responses, version, period, notes ->
                                viewModel.scoreAndSavePsqiAssessment(responses, version, period, notes)
                            },
                            onDeleteAssessment = { id -> viewModel.deletePsqiAssessment(id) }
                        )
                    }

                    InsightsTab.COMPARISON -> {
                        CombinedSection(uiState = state)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
