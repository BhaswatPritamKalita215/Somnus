package com.example.somnus.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.somnus.domain.analysis.DurationCalculator
import com.example.somnus.domain.model.SleepSession
import com.example.somnus.ui.components.ScientificHonestyBadge
import com.example.somnus.ui.components.SomnusConfirmDialog
import com.example.somnus.ui.components.SomnusTopBar
import com.example.somnus.ui.components.StatusDot
import kotlinx.coroutines.delay
import java.time.Duration

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToHistory: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    var completionNotes by remember { mutableStateOf("") }
    var endingDuration by remember { mutableStateOf<Duration?>(null) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            SomnusTopBar(
                title = "Somnus",
                subtitle = "Local-First Sleep Foundation"
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        if (state.isLoading && state.activeSession == null && state.recentSessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Primary Tracking Status Card (Idle vs Active)
                item {
                    val active = state.activeSession
                    if (active != null && active.isActive) {
                        ActiveSessionCard(
                            session = active,
                            onEndClick = { duration ->
                                endingDuration = duration
                                viewModel.promptEndSession()
                            },
                            onDiscardClick = { showDiscardConfirmation = true }
                        )
                    } else {
                        IdleSessionCard(
                            onStartClick = { viewModel.startSession() }
                        )
                    }
                }

                // Recent Completed Sessions Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Nights",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (state.recentSessions.isNotEmpty()) {
                            TextButton(onClick = onNavigateToHistory) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("View all")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Recent Sessions or Empty State
                if (state.recentSessions.isEmpty()) {
                    item {
                        EmptyStateCard()
                    }
                } else {
                    items(state.recentSessions, key = { it.id }) { session ->
                        RecentSessionItem(
                            session = session,
                            onClick = { onNavigateToDetail(session.id) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // End Session Confirmation Dialog with optional notes
        if (state.showEndConfirmation) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissEndSession() },
                title = { Text("End Sleep Session") },
                text = {
                    Column {
                        if (endingDuration != null) {
                            Text(
                                text = "Time in bed: ${DurationCalculator.formatDuration(endingDuration)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            text = "Save this sleep record to your local database?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = completionNotes,
                            onValueChange = { completionNotes = it },
                            label = { Text("Notes (optional)") },
                            placeholder = { Text("e.g. Read a book, woke up feeling rested") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val notes = completionNotes.trim().ifEmpty { null }
                        completionNotes = ""
                        viewModel.confirmEndSession(notes)
                    }) {
                        Text("Save & Complete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissEndSession() }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Discard Active Session Confirmation Dialog
        if (showDiscardConfirmation) {
            SomnusConfirmDialog(
                title = "Discard Active Session?",
                message = "This will discard the ongoing session without saving it to your history. This action cannot be undone.",
                confirmText = "Discard",
                dismissText = "Keep Tracking",
                isDestructive = true,
                onConfirm = {
                    showDiscardConfirmation = false
                    viewModel.discardActiveSession()
                },
                onDismiss = { showDiscardConfirmation = false }
            )
        }
    }
}

@Composable
private fun ActiveSessionCard(
    session: SleepSession,
    onEndClick: (Duration) -> Unit,
    onDiscardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var elapsed by remember(session.id) {
        mutableStateOf(DurationCalculator.calculateElapsed(session.startTime))
    }

    LaunchedEffect(session.id) {
        while (true) {
            elapsed = DurationCalculator.calculateElapsed(session.startTime)
            delay(1000)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(isActive = true)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SESSION IN PROGRESS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                ScientificHonestyBadge()
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Large elapsed time ticker
            Text(
                text = DurationCalculator.formatElapsed(elapsed),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Started at ${DurationCalculator.formatTime(session.startTime, session.zoneId)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onEndClick(elapsed) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("End Session")
                }

                OutlinedButton(
                    onClick = onDiscardClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Discard")
                }
            }
        }
    }
}

@Composable
private fun IdleSessionCard(
    onStartClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(isActive = false)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "READY TO TRACK",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ScientificHonestyBadge()
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Start Sleep Session",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Tap below when you go to bed. Records start and wake instants locally with zero sensor battery drain.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onStartClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Tracking Now")
            }
        }
    }
}

@Composable
private fun RecentSessionItem(
    session: SleepSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = DurationCalculator.formatDate(session.startTime, session.zoneId),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = DurationCalculator.formatInterval(session.startTime, session.endTime, session.zoneId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (session.isCorrected) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Manually corrected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = DurationCalculator.formatDuration(session.duration),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "in bed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No sleep records yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Start your first session tonight. Your sleep history will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
