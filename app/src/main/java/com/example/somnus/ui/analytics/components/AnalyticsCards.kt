package com.example.somnus.ui.analytics.components

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
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.somnus.domain.analytics.ComparisonDirection
import com.example.somnus.domain.analytics.DataQualityLevel
import com.example.somnus.domain.analytics.EvidenceLevel
import com.example.somnus.domain.analytics.MetricComparison
import com.example.somnus.domain.analytics.PeriodSleepMetrics
import com.example.somnus.domain.analytics.SleepDataQuality
import com.example.somnus.domain.analytics.SleepInsight

@Composable
fun OverviewMetricsCard(
    metrics: PeriodSleepMetrics,
    modifier: Modifier = Modifier
) {
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
                    text = "Sleep Metrics Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${metrics.totalNightsTracked} of ${metrics.totalDaysInRange} nights",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Stat: Average Duration
            if (metrics.averageDurationMinutes != null) {
                val totalMins = metrics.averageDurationMinutes.toInt()
                val hours = totalMins / 60
                val mins = totalMins % 60

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${hours}h ${mins}m",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "avg nightly sleep",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            } else {
                Text(
                    text = "No sleep records in selected range",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(14.dp))

            // 2x2 Grid of Secondary Metrics
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricSubItem(
                    label = "Typical Bedtime",
                    value = metrics.typicalBedtime?.toString() ?: "--",
                    subValue = metrics.bedtimeVariabilityMinutes?.let { "± ${it.toInt()}m variability" },
                    modifier = Modifier.weight(1f)
                )
                MetricSubItem(
                    label = "Typical Wake Time",
                    value = metrics.typicalWakeTime?.toString() ?: "--",
                    subValue = metrics.wakeTimeVariabilityMinutes?.let { "± ${it.toInt()}m variability" },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                MetricSubItem(
                    label = "Duration Variability",
                    value = metrics.durationVariabilityMinutes?.let { "± ${it.toInt()} min" } ?: "--",
                    subValue = "Night-to-night spread",
                    modifier = Modifier.weight(1f)
                )
                MetricSubItem(
                    label = "Average SOL",
                    value = metrics.averageSolMinutes?.let { "${it.toInt()} min" } ?: "Unavailable",
                    subValue = "Reported sleep onset latency",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricSubItem(
    label: String,
    value: String,
    subValue: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        if (subValue != null) {
            Text(text = subValue, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PeriodComparisonCard(
    comparisons: List<MetricComparison>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Period Comparison",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (comparisons.isEmpty()) {
                Text(
                    text = "No prior period data available for comparison.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                comparisons.forEach { comp ->
                    ComparisonRow(comp)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ComparisonRow(comp: MetricComparison) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = comp.metricName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (comp.absoluteDifference != null) {
                val sign = if (comp.absoluteDifference >= 0) "+" else ""
                val color = when (comp.direction) {
                    ComparisonDirection.INCREASED -> Color(0xFF86EFAC)
                    ComparisonDirection.DECREASED -> Color(0xFFFCA5A5)
                    ComparisonDirection.UNCHANGED -> MaterialTheme.colorScheme.onSurfaceVariant
                    ComparisonDirection.INSUFFICIENT_DATA -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = "$sign${comp.absoluteDifference} ${comp.unit}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            } else {
                Text(text = "Insufficient Data", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = comp.qualificationNote,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataQualityCard(
    quality: SleepDataQuality,
    modifier: Modifier = Modifier
) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Data Coverage & Quality",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val levelColor = when (quality.qualityLevel) {
                    DataQualityLevel.EXCELLENT -> Color(0xFF14532D)
                    DataQualityLevel.ADEQUATE -> Color(0xFF1E3A5F)
                    DataQualityLevel.SPARSE -> Color(0xFF78350F)
                    DataQualityLevel.INSUFFICIENT -> Color(0xFF7F1D1D)
                }
                val textColor = when (quality.qualityLevel) {
                    DataQualityLevel.EXCELLENT -> Color(0xFF86EFAC)
                    DataQualityLevel.ADEQUATE -> Color(0xFF93C5FD)
                    DataQualityLevel.SPARSE -> Color(0xFFFDE68A)
                    DataQualityLevel.INSUFFICIENT -> Color(0xFFFCA5A5)
                }

                Box(
                    modifier = Modifier
                        .background(levelColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = quality.qualityLevel.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${quality.nightsWithUsableData} of ${quality.totalDays} nights with sleep observations (${quality.coveragePercentage}%). Missing ${quality.missingNightsCount} nights.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (quality.sourceBreakdown.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Data Sources Contributing to Analysis:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    quality.sourceBreakdown.forEach { (source, count) ->
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${source.name}: $count nights",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InsightListCard(
    insights: List<SleepInsight>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Personalized Sleep Insights",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Deterministic, rules-based observations derived from your records",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (insights.isEmpty()) {
                Text(
                    text = "Log at least 3 nights to generate personalized schedule and duration insights.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    insights.forEach { insight ->
                        InsightItem(insight)
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightItem(insight: SleepInsight) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = insight.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val badgeColor = when (insight.evidenceLevel) {
                EvidenceLevel.ROBUST -> Color(0xFF14532D)
                EvidenceLevel.MODERATE -> Color(0xFF1E3A5F)
                EvidenceLevel.PRELIMINARY -> Color(0xFF78350F)
            }
            val textColor = when (insight.evidenceLevel) {
                EvidenceLevel.ROBUST -> Color(0xFF86EFAC)
                EvidenceLevel.MODERATE -> Color(0xFF93C5FD)
                EvidenceLevel.PRELIMINARY -> Color(0xFFFDE68A)
            }

            Box(
                modifier = Modifier
                    .background(badgeColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = insight.evidenceLevel.name,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = insight.observation,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = insight.explanation,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
        )
    }
}

@Composable
fun PhysiologicalPlaceholderCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.MonitorHeart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Physiological Metrics (HR, HRV, SpO₂, RR)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• Heart Rate, HRV (RMSSD), Blood Oxygen (SpO₂), and Respiratory Rate are charted only when actual records are imported from compatible wearable devices.\n" +
                        "• Somnus never invents or interpolates missing physiological readings as continuous monitoring.\n" +
                        "• Gaps and unavailable intervals remain explicitly visible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}
