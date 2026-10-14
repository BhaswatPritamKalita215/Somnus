package com.example.somnus.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.somnus.domain.model.PsqiAssessment
import com.example.somnus.domain.model.PsqiComponentScores
import com.example.somnus.domain.model.PsqiResponses
import com.example.somnus.domain.model.PsqiStatus
import com.example.somnus.domain.model.SyntheticPsqiFixtures

@Composable
fun PsqiSection(
    uiState: InsightsUiState,
    onScoreAssessment: (PsqiResponses, String, String, String?) -> Unit,
    onDeleteAssessment: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title & Description
        Column {
            Text(
                text = "Pittsburgh Sleep Quality Index",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Validated monthly 19-item sleep quality assessment (Buysse et al., 1989)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Instrument Copyright & Compliance Card
        InstrumentNoticeCard()

        // Synthetic Fixture Evaluation Runner
        SyntheticRunnerCard(
            onRunFixture = { fixture, name ->
                onScoreAssessment(fixture, "Synthetic-Fixture-v1", "Past 1 Month", "Tested via $name")
            },
            onOpenCustom = { showCustomDialog = true }
        )

        // Latest Assessment Result Card
        LatestPsqiCard(assessment = uiState.latestCompletedPsqi)

        // Historical Assessments List
        PsqiHistoryList(
            assessments = uiState.psqiAssessments,
            onDeleteAssessment = onDeleteAssessment
        )
    }

    if (showCustomDialog) {
        CustomPsqiDialog(
            onDismiss = { showCustomDialog = false },
            onSave = { responses ->
                showCustomDialog = false
                onScoreAssessment(responses, "Custom-Input-v1", "Past 1 Month", "Custom test input")
            }
        )
    }
}

@Composable
private fun InstrumentNoticeCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Authorized Instrument Policy",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The official 19-item PSQI questionnaire is copyrighted by the University of Pittsburgh. In compliance with clinical licensing rules, Somnus implements the exact pure Kotlin scoring engine and persistent data model without shipping unauthorized wording. Use the synthetic test fixtures below to verify reproducible scoring.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun SyntheticRunnerCard(
    onRunFixture: (PsqiResponses, String) -> Unit,
    onOpenCustom: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Synthetic Assessment Runner",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Score validated synthetic cases against the official Buysse (1989) engine:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onRunFixture(SyntheticPsqiFixtures.PERFECT_SLEEP, "Perfect Sleep") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Score 'Optimal Sleep' (Expected: 0 / 21)")
                }

                Button(
                    onClick = { onRunFixture(SyntheticPsqiFixtures.MODERATE_DISTURBANCE, "Moderate Disturbance") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Score 'Moderate Insomnia' (Expected: 8 / 21)")
                }

                Button(
                    onClick = { onRunFixture(SyntheticPsqiFixtures.MAXIMAL_SEVERITY, "Maximal Severity") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Score 'Max Severity' (Expected: 21 / 21)")
                }

                OutlinedButton(
                    onClick = onOpenCustom,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Enter Custom Numerical Responses")
                }
            }
        }
    }
}

@Composable
private fun LatestPsqiCard(assessment: PsqiAssessment?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Latest Assessment Score",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (assessment != null && assessment.status == PsqiStatus.COMPLETED && assessment.componentScores != null) {
                val scores = assessment.componentScores
                val isDisturbed = scores.isDisturbedSleep

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${scores.globalScore}",
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDisturbed) Color(0xFFF87171) else Color(0xFF4ADE80)
                        )
                        Text(
                            text = " / 21",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                if (isDisturbed) Color(0xFF7F1D1D) else Color(0xFF14532D),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isDisturbed) "Disturbed Sleep (> 5)" else "Good Sleep Quality (≤ 5)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDisturbed) Color(0xFFFCA5A5) else Color(0xFF86EFAC)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Seven Component Breakdown (0–3 each):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                ComponentScoreRow("C1: Subjective Sleep Quality", scores.c1SubjectiveQuality)
                ComponentScoreRow("C2: Sleep Latency (Duration + Frequency)", scores.c2SleepLatency)
                ComponentScoreRow("C3: Sleep Duration (<5h=3, >7h=0)", scores.c3SleepDuration)
                ComponentScoreRow("C4: Habitual Sleep Efficiency", scores.c4SleepEfficiency)
                ComponentScoreRow("C5: Sleep Disturbances", scores.c5SleepDisturbance)
                ComponentScoreRow("C6: Use of Sleep Medication", scores.c6UseOfMedication)
                ComponentScoreRow("C7: Daytime Dysfunction", scores.c7DaytimeDysfunction)
            } else {
                Text(
                    text = "No completed assessment available. Run one of the synthetic test cases above or log an assessment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ComponentScoreRow(label: String, score: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = "$score / 3",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (score >= 2) Color(0xFFF87171) else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PsqiHistoryList(
    assessments: List<PsqiAssessment>,
    onDeleteAssessment: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Previous Assessments (${assessments.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (assessments.isEmpty()) {
            Text(
                text = "No previous assessments recorded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            assessments.forEach { assessment ->
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = assessment.status.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (assessment.status == PsqiStatus.COMPLETED) Color(0xFF4ADE80) else Color(0xFFFBBF24)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "• ${assessment.reportingPeriod}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (assessment.componentScores != null) {
                                Text(
                                    text = "Global Score: ${assessment.componentScores.globalScore} / 21",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (!assessment.notes.isNullOrBlank()) {
                                Text(
                                    text = assessment.notes,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(onClick = { onDeleteAssessment(assessment.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomPsqiDialog(
    onDismiss: () -> Unit,
    onSave: (PsqiResponses) -> Unit
) {
    var bedTime by remember { mutableStateOf("23:00") }
    var wakeTime by remember { mutableStateOf("07:00") }
    var latencyMinutes by remember { mutableStateOf("25") }
    var sleepHours by remember { mutableStateOf("7.0") }
    var q5a by remember { mutableStateOf("1") }
    var disturbanceSum by remember { mutableStateOf("3") }
    var q6Meds by remember { mutableStateOf("0") }
    var q7Sleepy by remember { mutableStateOf("1") }
    var q8Enthusiasm by remember { mutableStateOf("1") }
    var q9Quality by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom PSQI Numerical Inputs") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = bedTime,
                    onValueChange = { bedTime = it },
                    label = { Text("Bed Time (HH:mm)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = wakeTime,
                    onValueChange = { wakeTime = it },
                    label = { Text("Wake Time (HH:mm)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = latencyMinutes,
                    onValueChange = { latencyMinutes = it },
                    label = { Text("Latency Minutes (Item 2)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sleepHours,
                    onValueChange = { sleepHours = it },
                    label = { Text("Actual Sleep Hours (Item 4)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = q5a,
                    onValueChange = { q5a = it },
                    label = { Text("Trouble <=30m (Item 5a: 0-3)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = q6Meds,
                    onValueChange = { q6Meds = it },
                    label = { Text("Sleep Meds (Item 6: 0-3)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = q9Quality,
                    onValueChange = { q9Quality = it },
                    label = { Text("Overall Quality (Item 9: 0-3)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val responses = PsqiResponses(
                        bedTime = bedTime,
                        wakeTime = wakeTime,
                        latencyMinutes = latencyMinutes.toIntOrNull() ?: 20,
                        actualSleepHours = sleepHours.toDoubleOrNull() ?: 7.0,
                        q5a = q5a.toIntOrNull() ?: 0,
                        q5b = 0, q5c = 0, q5d = 0, q5e = 0, q5f = 0, q5g = 0, q5h = 0, q5i = 0, q5j = 0,
                        q6 = q6Meds.toIntOrNull() ?: 0,
                        q7 = q7Sleepy.toIntOrNull() ?: 0,
                        q8 = q8Enthusiasm.toIntOrNull() ?: 0,
                        q9 = q9Quality.toIntOrNull() ?: 0
                    )
                    onSave(responses)
                }
            ) {
                Text("Score & Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
