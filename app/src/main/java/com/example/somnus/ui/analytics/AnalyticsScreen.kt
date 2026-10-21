package com.example.somnus.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.somnus.domain.analytics.DateRangeOption
import com.example.somnus.domain.analytics.DaySleepSummary
import com.example.somnus.ui.analytics.components.DataQualityCard
import com.example.somnus.ui.analytics.components.InsightListCard
import com.example.somnus.ui.analytics.components.OverviewMetricsCard
import com.example.somnus.ui.analytics.components.PeriodComparisonCard
import com.example.somnus.ui.analytics.components.PhysiologicalPlaceholderCard
import com.example.somnus.ui.analytics.components.ScheduleTrendChart
import com.example.somnus.ui.analytics.components.SleepDurationTrendChart
import com.example.somnus.ui.components.SomnusTopBar
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCustomDateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (showTopBar) {
                SomnusTopBar(
                    title = "Sleep Analytics",
                    subtitle = state.selectedRange.label
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Date Range Selection Chips
            DateRangeSelector(
                selectedOption = state.selectedOption,
                onSelectOption = { option ->
                    if (option == DateRangeOption.CUSTOM) {
                        showCustomDateDialog = true
                    } else {
                        viewModel.selectDateRangeOption(option)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (state.isLoading && state.report == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val report = state.report
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Selected Day Detail Card if user tapped a chart bar
                    state.selectedDayDetail?.let { day ->
                        SelectedDayCard(
                            day = day,
                            onDismiss = { viewModel.selectDayDetail(null) }
                        )
                    }

                    if (report != null) {
                        // 1. Overview Metrics Grid
                        OverviewMetricsCard(
                            metrics = report.periodMetrics
                        )

                        // 2. Duration Trend Chart
                        SleepDurationTrendChart(
                            summaries = report.dailySummaries,
                            averageDurationMinutes = report.periodMetrics.averageDurationMinutes,
                            startDate = state.selectedRange.startDate,
                            endDate = state.selectedRange.endDate,
                            onSelectDay = { viewModel.selectDayDetail(it) }
                        )

                        // 3. Schedule Consistency Chart
                        ScheduleTrendChart(
                            summaries = report.dailySummaries
                        )

                        // 4. Period Comparison Card
                        PeriodComparisonCard(comparisons = report.comparisons)

                        // 5. Data Quality and Observational Honesty Card
                        DataQualityCard(quality = report.dataQuality)

                        // 6. Actionable Scientific Insights
                        InsightListCard(insights = report.insights)

                        // 7. Physiological Metrics & Biomarker Gap Placeholder
                        PhysiologicalPlaceholderCard()
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (showCustomDateDialog) {
        CustomDateRangeDialog(
            currentStart = state.selectedRange.startDate,
            currentEnd = state.selectedRange.endDate,
            onConfirm = { start, end ->
                viewModel.setCustomDateRange(start, end)
                showCustomDateDialog = false
            },
            onDismiss = { showCustomDateDialog = false }
        )
    }
}

@Composable
private fun DateRangeSelector(
    selectedOption: DateRangeOption,
    onSelectOption: (DateRangeOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedOption == DateRangeOption.LAST_7_DAYS,
            onClick = { onSelectOption(DateRangeOption.LAST_7_DAYS) },
            label = { Text("7 Days") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        FilterChip(
            selected = selectedOption == DateRangeOption.LAST_30_DAYS,
            onClick = { onSelectOption(DateRangeOption.LAST_30_DAYS) },
            label = { Text("30 Days") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        FilterChip(
            selected = selectedOption == DateRangeOption.LAST_90_DAYS,
            onClick = { onSelectOption(DateRangeOption.LAST_90_DAYS) },
            label = { Text("90 Days") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        FilterChip(
            selected = selectedOption == DateRangeOption.CUSTOM,
            onClick = { onSelectOption(DateRangeOption.CUSTOM) },
            label = { Text("Custom") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
    }
}

@Composable
private fun SelectedDayCard(
    day: DaySleepSummary,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d, yyyy") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val hasSleepData = day.totalSleepDurationMinutes > 0

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = day.sleepDate.format(dateFormatter),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close detail",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!hasSleepData) {
                Text(
                    text = "No sleep recorded for this night.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Actual Sleep",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val hours = day.totalSleepDurationMinutes / 60
                        val mins = day.totalSleepDurationMinutes % 60
                        Text(
                            text = "${hours}h ${mins}m",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Time in Bed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val tibHours = day.totalTimeInBedMinutes / 60
                        val tibMins = day.totalTimeInBedMinutes % 60
                        Text(
                            text = "${tibHours}h ${tibMins}m",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (day.bedtime != null) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bedtime",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = day.bedtime.format(timeFormatter),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (day.wakeTime != null) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Wake Time",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = day.wakeTime.format(timeFormatter),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (day.solMinutes != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Sleep Onset Latency (SOL): ${day.solMinutes} min",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                if (day.sources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Sources: ${day.sources.joinToString(", ") { it.name }} (${day.sessionCount} session(s))",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomDateRangeDialog(
    currentStart: LocalDate,
    currentEnd: LocalDate,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var daysBack by remember { mutableIntStateOf(14) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Select Custom Range")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Choose the number of recent days to analyze (ending today):",
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(14, 21, 60, 180).forEach { days ->
                        OutlinedButton(
                            onClick = { daysBack = days },
                            colors = if (daysBack == days) {
                                androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            } else {
                                androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Text("$days d")
                        }
                    }
                }

                val targetStart = LocalDate.now().minusDays(daysBack.toLong() - 1)
                val targetEnd = LocalDate.now()
                Text(
                    text = "Span: $targetStart to $targetEnd ($daysBack days)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val start = LocalDate.now().minusDays(daysBack.toLong() - 1)
                    val end = LocalDate.now()
                    onConfirm(start, end)
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
