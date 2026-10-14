package com.example.somnus.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.somnus.domain.analysis.SolSummary
import com.example.somnus.domain.model.SolMeasurementMethod
import com.example.somnus.domain.model.SolQualityFlag
import com.example.somnus.domain.model.SolRecord
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@Composable
fun SolSection(
    uiState: InsightsUiState,
    onSaveRecord: (LocalDate, Int, SolMeasurementMethod, String, String?, String?) -> Unit,
    onSaveWithTimestamps: (java.time.Instant, java.time.Instant, LocalDate, SolMeasurementMethod, String?) -> Unit,
    onDeleteRecord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showLogDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Sleep Onset Latency (SOL)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Time taken to transition from full wakefulness to sleep",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = { showLogDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Log Nightly Latency")
        }

        // Latest Nightly Card
        LatestSolCard(record = uiState.latestSolRecord)

        // Aggregates: Weekly & Monthly Summary Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(
                summary = uiState.weeklySolSummary,
                title = "7-Day Summary",
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                summary = uiState.monthlySolSummary,
                title = "30-Day Summary",
                modifier = Modifier.weight(1f)
            )
        }

        // Historical Trend Chart
        SolTrendChart(records = uiState.solRecords)

        // Historical Records List
        HistoricalSolList(
            records = uiState.solRecords,
            onDeleteRecord = onDeleteRecord
        )
    }

    if (showLogDialog) {
        LogSolDialog(
            onDismiss = { showLogDialog = false },
            onSaveManual = { date, minutes, method, notes ->
                showLogDialog = false
                onSaveRecord(date, minutes, method, "manual", null, notes)
            },
            onSaveCalculated = { attemptTime, onsetTime, date, method, notes ->
                showLogDialog = false
                onSaveWithTimestamps(attemptTime, onsetTime, date, method, notes)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LatestSolCard(record: SolRecord?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Most Recent Latency",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (record != null) {
                    Text(
                        text = record.reportingDate.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (record != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${record.latencyMinutes}",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (record.latencyMinutes > 30) Color(0xFFF87171) else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "minutes",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val methodLabel = when (record.method) {
                        SolMeasurementMethod.SELF_REPORTED -> "Self-Reported"
                        SolMeasurementMethod.ALGORITHM_ESTIMATE -> "Algorithmic Estimate"
                        SolMeasurementMethod.WEARABLE_IMPORT -> "Wearable Hardware"
                    }
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = methodLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Text(
                        text = "TZ: ${record.zoneId.id}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (record.qualityFlags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        record.qualityFlags.forEach { flag ->
                            val flagLabel = when (flag) {
                                SolQualityFlag.UNCERTAIN_TIMING -> "Uncertain Timing"
                                SolQualityFlag.EDITED_MANUALLY -> "Edited"
                                SolQualityFlag.OUTLIER_EXCESSIVE_LATENCY -> "Prolonged (>120m)"
                                SolQualityFlag.MISSING_SLEEP_ATTEMPT -> "No Attempt Time"
                            }
                            Box(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = flagLabel,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "No sleep onset latency recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    summary: SolSummary?,
    title: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (summary != null && summary.recordedNightsCount > 0) {
                Text(
                    text = "Avg: ${summary.averageLatencyMinutes ?: "--"} min",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Median: ${summary.medianLatencyMinutes ?: "--"} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Range: ${summary.minLatencyMinutes ?: "--"} - ${summary.maxLatencyMinutes ?: "--"} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Nights: ${summary.recordedNightsCount} / ${summary.totalDaysInPeriod}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (summary.missingNightsCount > 0) {
                    Text(
                        text = "Missing: ${summary.missingNightsCount} nights",
                        fontSize = 11.sp,
                        color = Color(0xFFFBBF24)
                    )
                }
            } else {
                Text(
                    text = "Insufficient data",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "0 nights logged",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HistoricalSolList(
    records: List<SolRecord>,
    onDeleteRecord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Recorded Nights (${records.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (records.isEmpty()) {
            Text(
                text = "No SOL records yet. Tap 'Log Nightly Latency' to record tonight's sleep onset.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            records.take(10).forEach { record ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = record.reportingDate.toString(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${record.latencyMinutes} min",
                                    fontWeight = FontWeight.Bold,
                                    color = if (record.latencyMinutes > 30) Color(0xFFF87171) else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "• ${record.method.name.lowercase().replace('_', ' ')}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!record.notes.isNullOrBlank()) {
                                Text(
                                    text = record.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(onClick = { onDeleteRecord(record.id) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogSolDialog(
    onDismiss: () -> Unit,
    onSaveManual: (LocalDate, Int, SolMeasurementMethod, String?) -> Unit,
    onSaveCalculated: (java.time.Instant, java.time.Instant, LocalDate, SolMeasurementMethod, String?) -> Unit
) {
    var entryMode by remember { mutableStateOf(0) } // 0 = Direct minutes, 1 = Clock times
    var dateString by remember { mutableStateOf(LocalDate.now().toString()) }
    var latencyMinutesText by remember { mutableStateOf("20") }
    var attemptTimeText by remember { mutableStateOf("23:15") }
    var onsetTimeText by remember { mutableStateOf("23:45") }
    var selectedMethod by remember { mutableStateOf(SolMeasurementMethod.SELF_REPORTED) }
    var notesText by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Sleep Onset Latency") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = entryMode == 0,
                        onClick = { entryMode = 0 },
                        label = { Text("Direct Minutes") }
                    )
                    FilterChip(
                        selected = entryMode == 1,
                        onClick = { entryMode = 1 },
                        label = { Text("In-Bed to Onset") }
                    )
                }

                OutlinedTextField(
                    value = dateString,
                    onValueChange = { dateString = it },
                    label = { Text("Reporting Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (entryMode == 0) {
                    OutlinedTextField(
                        value = latencyMinutesText,
                        onValueChange = { latencyMinutesText = it },
                        label = { Text("Latency (Minutes)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = attemptTimeText,
                        onValueChange = { attemptTimeText = it },
                        label = { Text("Attempt to Sleep (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = onsetTimeText,
                        onValueChange = { onsetTimeText = it },
                        label = { Text("Sleep Onset Time (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    text = "Method:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedMethod == SolMeasurementMethod.SELF_REPORTED,
                        onClick = { selectedMethod = SolMeasurementMethod.SELF_REPORTED },
                        label = { Text("Self-Reported") }
                    )
                    FilterChip(
                        selected = selectedMethod == SolMeasurementMethod.ALGORITHM_ESTIMATE,
                        onClick = { selectedMethod = SolMeasurementMethod.ALGORITHM_ESTIMATE },
                        label = { Text("Algorithm") }
                    )
                }

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedDate = try {
                        LocalDate.parse(dateString.trim())
                    } catch (e: Exception) {
                        validationError = "Invalid date format. Use YYYY-MM-DD."
                        return@Button
                    }

                    if (entryMode == 0) {
                        val minutes = latencyMinutesText.trim().toIntOrNull()
                        if (minutes == null || minutes < 0) {
                            validationError = "Enter valid positive minutes."
                            return@Button
                        }
                        onSaveManual(parsedDate, minutes, selectedMethod, notesText.ifBlank { null })
                    } else {
                        try {
                            val attemptLocal = LocalTime.parse(attemptTimeText.trim())
                            val onsetLocal = LocalTime.parse(onsetTimeText.trim())
                            val zone = ZoneId.systemDefault()

                            // Construct Instants (handling crossing midnight)
                            val attemptZdt = ZonedDateTime.of(parsedDate, attemptLocal, zone)
                            val onsetDate = if (onsetLocal.isBefore(attemptLocal)) parsedDate.plusDays(1) else parsedDate
                            val onsetZdt = ZonedDateTime.of(onsetDate, onsetLocal, zone)

                            onSaveCalculated(
                                attemptZdt.toInstant(),
                                onsetZdt.toInstant(),
                                parsedDate,
                                selectedMethod,
                                notesText.ifBlank { null }
                            )
                        } catch (e: Exception) {
                            validationError = "Invalid time format: ${e.message}"
                        }
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
