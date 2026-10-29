package com.example.somnus.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import com.example.somnus.BuildConfig
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.somnus.domain.analysis.DurationCalculator
import com.example.somnus.domain.dashboard.CoverageCompletenessTier
import com.example.somnus.domain.dashboard.CrossMetricInsight
import com.example.somnus.domain.dashboard.DashboardDayData
import com.example.somnus.domain.dashboard.DataCoverageSummary
import com.example.somnus.domain.dashboard.InsightConfidence
import com.example.somnus.domain.model.SleepSession
import com.example.somnus.ui.components.ScientificHonestyBadge
import com.example.somnus.ui.components.SomnusConfirmDialog
import com.example.somnus.ui.components.SomnusTopBar
import com.example.somnus.ui.components.StatusDot
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToHistory: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToActivity: () -> Unit = {},
    onNavigateToNutrition: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToAssistant: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var recoveryHoursText by remember { mutableStateOf("8.0") }
    var recoveryNotes by remember { mutableStateOf("") }
    var completionNotes by remember { mutableStateOf("") }
    var endingDuration by remember { mutableStateOf<Duration?>(null) }

    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.startSession()
    }

    val healthConnectLauncher = rememberLauncherForActivityResult(
        contract = androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) {
        viewModel.syncHealthConnect()
    }

    val onStartTrackingClick: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.startSession()
            }
        } else {
            viewModel.startSession()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.feedbackMessage) {
        state.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissFeedback()
        }
    }

    Scaffold(
        topBar = {
            SomnusTopBar(
                title = "Somnus",
                subtitle = "Unified Health Dashboard",
                actions = {
                    if (BuildConfig.IS_DEBUG_SIGNED_RELEASE) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "TEST BUILD — debug signed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onNavigateToSettings
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        if (state.isLoading && state.activeSession == null && !state.dayData.hasAnyData && state.recentSessions.isEmpty()) {
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
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Unified Date Navigator with Sync Button
                item {
                    DashboardDateNavigator(
                        selectedDate = state.selectedDate,
                        isSyncing = state.isSyncing,
                        onPreviousDay = { viewModel.stepDate(-1) },
                        onNextDay = { viewModel.stepDate(1) },
                        onSelectToday = { viewModel.selectToday() },
                        onSyncHealthConnect = {
                            viewModel.syncHealthConnect(
                                onPermissionRequired = { perms -> healthConnectLauncher.launch(perms) }
                            )
                        }
                    )
                }

                // 2. Active Session Banner / Quick Tracking Card
                item {
                    val active = state.activeSession
                    if (active != null && active.isActive) {
                        ActiveSessionCard(
                            session = active,
                            isStale = state.isSessionStale,
                            isInterrupted = state.isTrackingInterrupted,
                            onResumeTrackingClick = { viewModel.resumeTracking() },
                            onEndClick = { duration ->
                                endingDuration = duration
                                viewModel.promptEndSession()
                            },
                            onRecoverClick = { showRecoveryDialog = true },
                            onDiscardClick = { showDiscardConfirmation = true }
                        )
                    } else {
                        IdleSessionCard(
                            onStartClick = onStartTrackingClick
                        )
                    }
                }

                // 3. Sleep Section Card
                item {
                    DashboardSleepCard(
                        data = state.dayData,
                        onViewDetails = onNavigateToHistory,
                        onViewAnalytics = onNavigateToAnalytics,
                        onSessionClick = onNavigateToDetail
                    )
                }

                // 4. Activity / Steps Section Card
                item {
                    DashboardActivityCard(
                        data = state.dayData,
                        onViewActivity = onNavigateToActivity
                    )
                }

                // 5. Nutrition Section Card
                item {
                    DashboardNutritionCard(
                        data = state.dayData,
                        onViewNutrition = onNavigateToNutrition
                    )
                }

                // 6. Energy Expenditure Card
                item {
                    DashboardEnergyCard(
                        data = state.dayData
                    )
                }

                // 7. Cross-Metric Insights Section
                item {
                    CrossMetricInsightsSection(
                        insights = state.crossMetricInsights
                    )
                }

                // 8. Data Quality & Coverage Summary
                state.coverageSummary?.let { coverage ->
                    item {
                        DashboardCoverageCard(coverage = coverage)
                    }
                }

                // 9. Somnus AI Assistant Entry Card
                item {
                    DashboardAssistantCard(
                        onOpenAssistant = onNavigateToAssistant
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // End session confirmation dialog
    if (state.showEndConfirmation) {
        val duration = endingDuration ?: Duration.ZERO
        val formattedDuration = DurationCalculator.formatDuration(duration)

        AlertDialog(
            onDismissRequest = { viewModel.dismissEndSession() },
            title = { Text("Complete Sleep Session") },
            text = {
                Column {
                    Text("Session duration: $formattedDuration")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = completionNotes,
                        onValueChange = { completionNotes = it },
                        label = { Text("Session Notes (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val notes = completionNotes.trim().ifEmpty { null }
                        viewModel.confirmEndSession(notes)
                        completionNotes = ""
                        endingDuration = null
                    }
                ) {
                    Text("Save Session")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        viewModel.dismissEndSession()
                        endingDuration = null
                    }
                ) {
                    Text("Resume Tracking")
                }
            }
        )
    }

    // Discard active session confirmation dialog
    if (showDiscardConfirmation) {
        SomnusConfirmDialog(
            title = "Discard Active Session?",
            message = "This will immediately terminate and delete the ongoing tracking session without saving it to your local records.",
            confirmText = "Discard",
            isDestructive = true,
            onConfirm = {
                showDiscardConfirmation = false
                viewModel.discardActiveSession()
            },
            onDismiss = { showDiscardConfirmation = false }
        )
    }

    // Stale session recovery dialog
    if (showRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { showRecoveryDialog = false },
            title = { Text("Recover Sleep Session") },
            text = {
                Column {
                    Text(
                        text = "This session has been active for more than 16 hours. You can specify its estimated duration to recover the sleep record accurately.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = recoveryHoursText,
                        onValueChange = { recoveryHoursText = it },
                        label = { Text("Estimated Duration (hours)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = recoveryNotes,
                        onValueChange = { recoveryNotes = it },
                        label = { Text("Correction Notes (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val hours = recoveryHoursText.toDoubleOrNull() ?: 8.0
                        val active = state.activeSession
                        if (active != null) {
                            val estimatedDurationMs = (hours * 3600 * 1000).toLong()
                            val correctedEnd = active.startTime.plusMillis(estimatedDurationMs)
                            val notes = recoveryNotes.trim().ifEmpty { "Recovered stale session ($hours hrs estimated)" }
                            viewModel.recoverStaleSession(correctedEnd, notes)
                        }
                        showRecoveryDialog = false
                    }
                ) {
                    Text("Recover Session")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRecoveryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Morning check-in dialog after ending sleep session
    state.pendingCheckInSessionId?.let { sessionId ->
        MorningCheckInDialog(
            sessionId = sessionId,
            onSave = { moodScore, moodLabel, restedScore, notes ->
                viewModel.saveMorningCheckIn(sessionId, moodScore, moodLabel, restedScore, notes)
            },
            onSkip = {
                viewModel.skipMorningCheckIn()
            }
        )
    }
}

/**
 * Top Date Navigator for switching between current and historical dates.
 */
@Composable
fun DashboardDateNavigator(
    selectedDate: LocalDate,
    isSyncing: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onSelectToday: () -> Unit,
    onSyncHealthConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isToday = selectedDate == LocalDate.now()
    val formatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousDay) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Day")
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { onSelectToday() }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isToday) "Today" else selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (isToday) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = selectedDate.format(formatter),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onNextDay,
                enabled = !isToday
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next Day",
                    tint = if (!isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            IconButton(
                onClick = onSyncHealthConnect,
                enabled = !isSyncing
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Sync Health Connect",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Card representing sleep metrics for the selected date.
 */
@Composable
fun DashboardSleepCard(
    data: DashboardDayData,
    onViewDetails: () -> Unit,
    onViewAnalytics: () -> Unit,
    onSessionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sleep",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(onClick = onViewDetails) {
                    Text("History", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val summary = data.sleepSummary
            if (summary != null && summary.totalSleepDurationMinutes > 0) {
                val hours = summary.totalSleepDurationMinutes / 60
                val mins = summary.totalSleepDurationMinutes % 60

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "${hours}h ${mins}m",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val sourceLabel = summary.sources.joinToString { it.name }
                        Text(
                            text = "Duration • $sourceLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Bedtime & Wake Time
                    if (summary.bedtime != null && summary.wakeTime != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${summary.bedtime} → ${summary.wakeTime}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Schedule",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // SOL and PSQI Badges
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (data.solRecord != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "SOL: ${data.solRecord.latencyMinutes} min",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    val psqiScore = data.latestPsqi?.componentScores?.globalScore
                    if (psqiScore != null) {
                        val classification = if (data.latestPsqi.componentScores.isDisturbedSleep) "Poor Sleep Quality (> 5)" else "Good Sleep Quality (<= 5)"
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "PSQI: $psqiScore ($classification)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "No sleep recorded for this clinical date.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Note: Overnight sessions belong to the clinical sleep opportunity of the preceding evening.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onViewAnalytics) {
                    Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Detailed Analytics", fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Card representing daily steps and activity progress.
 */
@Composable
fun DashboardActivityCard(
    data: DashboardDayData,
    onViewActivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(onClick = onViewActivity) {
                    Text("Details", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val stepSummary = data.stepSummary
            if (stepSummary != null && stepSummary.totalSteps > 0) {
                val progress = (stepSummary.totalSteps.toFloat() / stepSummary.goalSteps.toFloat()).coerceIn(0f, 1f)
                val percent = (progress * 100).toInt()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "%,d".format(stepSummary.totalSteps),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Goal: %,d steps ($percent%)".format(stepSummary.goalSteps),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = stepSummary.rationale.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            } else {
                Text(
                    text = "No step data recorded for this date.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Phone hardware sensor or Health Connect wearable sync will populate activity.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * Card representing dietary energy and macronutrient intake for the selected day.
 */
@Composable
fun DashboardNutritionCard(
    data: DashboardDayData,
    onViewNutrition: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Nutrition",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(onClick = onViewNutrition) {
                    Text("Food Log", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val nutrition = data.nutritionSummary
            if (nutrition != null && nutrition.entries.isNotEmpty()) {
                val cal = nutrition.totalCalories.toInt()
                val target = data.calorieTarget
                val progress = (cal.toFloat() / target.toFloat()).coerceIn(0f, 1f)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "$cal kcal",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Target: $target kcal (${nutrition.entries.size} items logged)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "P: ${nutrition.totalProtein.toInt()}g • C: ${nutrition.totalCarbs.toInt()}g • F: ${nutrition.totalFat.toInt()}g",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Macros",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            } else {
                Text(
                    text = "No meals or packaged foods logged for this date.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Tap Food Log to scan retail barcodes or enter nutritional labels.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * Card representing recorded active and total energy expenditure from Health Connect.
 */
@Composable
fun DashboardEnergyCard(
    data: DashboardDayData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Energy Expenditure",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val energy = data.energyExpenditure
            val active = energy?.activeCaloriesKcal?.roundToInt()
            val total = energy?.totalCaloriesKcal?.roundToInt()

            if (active != null || total != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = if (active != null) "$active kcal" else "—",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Active Burn (movement)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (total != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$total kcal",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Total Burn (BMR + Active)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (energy?.sourcePackage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Source: ${energy.sourcePackage}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                Text(
                    text = if (data.hasHealthConnectEnergyPermissions) {
                        "No energy expenditure records available in Health Connect for this day."
                    } else {
                        "Health Connect energy expenditure permissions not granted."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Unrecorded burn reflects unmeasured sensor intervals, not zero energy.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * Cross-metric analytical insights section.
 */
@Composable
fun CrossMetricInsightsSection(
    insights: List<CrossMetricInsight>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Insights,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Cross-Metric Insights",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Empirical associations across sleep, steps, and nutrition",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (insights.isNotEmpty()) {
            insights.forEach { insight ->
                CrossMetricInsightCard(insight = insight)
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Gathering cross-metric data...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Record at least 3 paired days of sleep sessions and physical steps to unlock statistical correlations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CrossMetricInsightCard(
    insight: CrossMetricInsight,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = insight.type.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (insight.confidence) {
                        InsightConfidence.SUFFICIENT_DATA -> MaterialTheme.colorScheme.primaryContainer
                        InsightConfidence.PRELIMINARY -> MaterialTheme.colorScheme.tertiaryContainer
                        InsightConfidence.INSUFFICIENT -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = insight.confidence.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (insight.confidence) {
                            InsightConfidence.SUFFICIENT_DATA -> MaterialTheme.colorScheme.onPrimaryContainer
                            InsightConfidence.PRELIMINARY -> MaterialTheme.colorScheme.onTertiaryContainer
                            InsightConfidence.INSUFFICIENT -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = insight.headline,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = insight.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = insight.caveat,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

/**
 * Multi-domain data coverage and completeness card.
 */
@Composable
fun DashboardCoverageCard(
    coverage: DataCoverageSummary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Data Coverage & Completeness",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = coverage.completenessTier.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = coverage.summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Domain coverage row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CoveragePill(label = "Sleep", count = coverage.sleepDaysWithData, total = coverage.totalDays)
                CoveragePill(label = "Steps", count = coverage.stepDaysWithData, total = coverage.totalDays)
                CoveragePill(label = "Food", count = coverage.nutritionDaysWithData, total = coverage.totalDays)
                CoveragePill(label = "Energy", count = coverage.energyDaysWithData, total = coverage.totalDays)
            }
        }
    }
}

@Composable
fun CoveragePill(label: String, count: Int, total: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = "$count/$total",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    }
}

/**
 * Idle card encouraging starting tracking.
 */
@Composable
fun IdleSessionCard(
    onStartClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Track Sleep Tonight",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Sensor-validated phone tracking & wearable sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = onStartClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start")
            }
        }
    }
}

/**
 * Active Tracking card with live timer and controls.
 */
@Composable
fun ActiveSessionCard(
    session: SleepSession,
    isStale: Boolean,
    isInterrupted: Boolean = false,
    onResumeTrackingClick: () -> Unit = {},
    onEndClick: (Duration) -> Unit,
    onRecoverClick: () -> Unit,
    onDiscardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var elapsedDuration by remember {
        mutableStateOf(Duration.between(session.startTime, java.time.Instant.now()))
    }

    LaunchedEffect(session.startTime) {
        while (true) {
            elapsedDuration = Duration.between(session.startTime, java.time.Instant.now())
            delay(1000)
        }
    }

    val formattedDuration = DurationCalculator.formatDuration(elapsedDuration)

    val containerColor = when {
        isStale -> MaterialTheme.colorScheme.errorContainer
        isInterrupted -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    val contentColor = when {
        isStale -> MaterialTheme.colorScheme.onErrorContainer
        isInterrupted -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    val titleText = when {
        isStale -> "Tracking Warning (Stale)"
        isInterrupted -> "Tracking Interrupted"
        else -> "Overnight Tracking Active"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(isActive = !isInterrupted)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }

                IconButton(
                    onClick = onDiscardClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Discard Session",
                        tint = contentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = formattedDuration,
                style = MaterialTheme.typography.headlineLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )

            if (isInterrupted) {
                Spacer(modifier = Modifier.height(6.dp))
                val wasRebooted = remember(session.startTime) {
                    try {
                        val bootTime = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()
                        session.startTime.toEpochMilli() < bootTime
                    } catch (e: Exception) {
                        false
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (wasRebooted) {
                            "Phone tracking was interrupted by a device reboot. Your session is preserved."
                        } else {
                            "Phone tracking was stopped (e.g. system process reclamation). Your session is preserved."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                }
            } else if (isStale) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Session exceeds 16 hours. You can recover with estimated duration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isInterrupted) {
                    Button(
                        onClick = onResumeTrackingClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Resume")
                    }
                }

                Button(
                    onClick = { onEndClick(elapsedDuration) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Wake Up & End")
                }

                if (isStale) {
                    OutlinedButton(
                        onClick = onRecoverClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Recover")
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardAssistantCard(
    onOpenAssistant: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenAssistant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Local rules assistant",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Ask questions about your sleep, activity, and nutrition. 100% on-device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open Assistant",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

