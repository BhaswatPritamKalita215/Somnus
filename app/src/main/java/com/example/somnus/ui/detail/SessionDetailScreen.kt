package com.example.somnus.ui.detail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import com.example.somnus.domain.analysis.SessionValidator
import com.example.somnus.domain.analysis.ValidationResult
import com.example.somnus.domain.model.SleepSession
import com.example.somnus.ui.components.ScientificHonestyBadge
import com.example.somnus.ui.components.SomnusConfirmDialog
import com.example.somnus.ui.components.SomnusTopBar
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    viewModel: SessionDetailViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) {
            onNavigateBack()
        }
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            snackbarHostState.showSnackbar("Session correction saved")
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            SomnusTopBar(
                title = "Session Details",
                subtitle = "Record & Verification",
                onBackClick = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        if (state.isLoading && state.session == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.session == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Session not found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val session = state.session!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Hero Duration Card
                DurationHeroCard(session = session)

                // Scientific Honesty & Transparency Notice
                TransparencyNoticeCard(session = session)

                // Timestamp Breakdown Card
                TimestampBreakdownCard(session = session)

                // User Notes Card
                if (!session.notes.isNullOrBlank()) {
                    NotesCard(notes = session.notes)
                }

                // Diagnostics & Metadata Card
                MetadataCard(session = session)

                // Action Buttons (Edit / Delete)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Correct Times")
                    }

                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Delete Confirmation Dialog
        if (showDeleteConfirm) {
            SomnusConfirmDialog(
                title = "Delete this session?",
                message = "This sleep record will be permanently deleted from your local device database. This action cannot be undone.",
                confirmText = "Delete",
                dismissText = "Cancel",
                isDestructive = true,
                onConfirm = {
                    showDeleteConfirm = false
                    viewModel.deleteSession()
                },
                onDismiss = { showDeleteConfirm = false }
            )
        }

        // Edit / Correction Dialog
        if (showEditDialog && state.session != null) {
            EditSessionDialog(
                session = state.session!!,
                onDismiss = { showEditDialog = false },
                onSave = { newStart, newEnd, newNotes ->
                    showEditDialog = false
                    viewModel.correctSession(newStart, newEnd, newNotes)
                }
            )
        }
    }
}

@Composable
private fun DurationHeroCard(session: SleepSession, modifier: Modifier = Modifier) {
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
                Text(
                    text = "TIME IN BED",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                ScientificHonestyBadge(isCorrected = session.isCorrected)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = DurationCalculator.formatDuration(session.duration),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${DurationCalculator.formatDate(session.startTime, session.zoneId)} • ${
                    DurationCalculator.formatInterval(session.startTime, session.endTime, session.zoneId)
                }",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TransparencyNoticeCard(session: SleepSession, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Scientific Honesty Notice",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "This record reflects user-recorded Time in Bed (TIB). No sensor measurements, heart rate, or physiological sleep stages have been predicted or invented.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TimestampBreakdownCard(session: SleepSession, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Timestamps & Zone",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            DetailRow(
                icon = Icons.Default.CalendarToday,
                label = "Start Instant",
                value = "${DurationCalculator.formatDate(session.startTime, session.zoneId)} at ${DurationCalculator.formatTime(session.startTime, session.zoneId)}"
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            DetailRow(
                icon = Icons.Default.Schedule,
                label = "End Instant",
                value = if (session.endTime != null) {
                    "${DurationCalculator.formatDate(session.endTime, session.zoneId)} at ${DurationCalculator.formatTime(session.endTime, session.zoneId)}"
                } else {
                    "Session still active"
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            DetailRow(
                icon = Icons.Default.AccessTime,
                label = "Timezone",
                value = "${session.zoneId.id} (${session.startTime.atZone(session.zoneId).offset})"
            )
        }
    }
}

@Composable
private fun NotesCard(notes: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "User Notes",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetadataCard(session: SleepSession, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Record Metadata",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            DetailRow(
                icon = Icons.Default.Fingerprint,
                label = "Session ID",
                value = session.id
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Status: ${session.status.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "Source: ${session.source.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (session.isCorrected) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Last corrected: ${DurationCalculator.formatDate(session.updatedAt, session.zoneId)} at ${DurationCalculator.formatTime(session.updatedAt, session.zoneId)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * Correction dialog allowing users to adjust start and end instants with live validation.
 */
@Composable
private fun EditSessionDialog(
    session: SleepSession,
    onDismiss: () -> Unit,
    onSave: (Instant, Instant?, String?) -> Unit
) {
    var startInstant by remember { mutableStateOf(session.startTime) }
    var endInstant by remember { mutableStateOf(session.endTime ?: session.startTime.plus(Duration.ofHours(8))) }
    var notesText by remember { mutableStateOf(session.notes ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun revalidate(s: Instant, e: Instant?): Boolean {
        val res = SessionValidator.validate(start = s, end = e, status = session.status)
        return if (res is ValidationResult.Invalid) {
            validationError = res.reason
            false
        } else {
            validationError = null
            true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Correct Sleep Times") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Adjust recorded bedtime and wake time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Start Time Adjusters (-1h, -15m, +15m, +1h)
                Text(
                    text = "Bedtime: ${DurationCalculator.formatTime(startInstant, session.zoneId)} (${DurationCalculator.formatDate(startInstant, session.zoneId)})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AdjustmentButton("-1h") {
                        startInstant = startInstant.minus(Duration.ofHours(1))
                        revalidate(startInstant, endInstant)
                    }
                    AdjustmentButton("-15m") {
                        startInstant = startInstant.minus(Duration.ofMinutes(15))
                        revalidate(startInstant, endInstant)
                    }
                    AdjustmentButton("+15m") {
                        startInstant = startInstant.plus(Duration.ofMinutes(15))
                        revalidate(startInstant, endInstant)
                    }
                    AdjustmentButton("+1h") {
                        startInstant = startInstant.plus(Duration.ofHours(1))
                        revalidate(startInstant, endInstant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // End Time Adjusters (-1h, -15m, +15m, +1h)
                Text(
                    text = "Wake time: ${DurationCalculator.formatTime(endInstant, session.zoneId)} (${DurationCalculator.formatDate(endInstant, session.zoneId)})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AdjustmentButton("-1h") {
                        endInstant = endInstant.minus(Duration.ofHours(1))
                        revalidate(startInstant, endInstant)
                    }
                    AdjustmentButton("-15m") {
                        endInstant = endInstant.minus(Duration.ofMinutes(15))
                        revalidate(startInstant, endInstant)
                    }
                    AdjustmentButton("+15m") {
                        endInstant = endInstant.plus(Duration.ofMinutes(15))
                        revalidate(startInstant, endInstant)
                    }
                    AdjustmentButton("+1h") {
                        endInstant = endInstant.plus(Duration.ofHours(1))
                        revalidate(startInstant, endInstant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Calculated Duration preview
                val previewDuration = if (!endInstant.isBefore(startInstant)) {
                    Duration.between(startInstant, endInstant)
                } else null

                Text(
                    text = "Resulting Duration: ${DurationCalculator.formatDuration(previewDuration)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                if (validationError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (revalidate(startInstant, endInstant)) {
                        onSave(startInstant, endInstant, notesText.trim().ifEmpty { null })
                    }
                },
                enabled = validationError == null
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AdjustmentButton(
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}
