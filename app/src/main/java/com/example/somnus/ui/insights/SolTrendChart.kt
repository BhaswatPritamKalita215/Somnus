package com.example.somnus.ui.insights

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.somnus.domain.model.SolRecord

/**
 * Custom Jetpack Compose Canvas chart illustrating nightly Sleep Onset Latency trend
 * and clinical reference thresholds.
 */
@Composable
fun SolTrendChart(
    records: List<SolRecord>,
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
                    text = "Nightly SOL Trend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFFE57373), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "30m Reference",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Log at least one night to view latency chart",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Take chronological list of up to 14 recent nights
                val chartRecords = records.sortedBy { it.reportingDate }.takeLast(14)
                val maxMinutes = maxOf(60, (chartRecords.maxOfOrNull { it.latencyMinutes } ?: 30) + 10)

                val primaryColor = MaterialTheme.colorScheme.primary
                val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                val thresholdColor = Color(0xFFE57373)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    // Draw 30m reference line
                    val thresholdY = canvasHeight - ((30.0f / maxMinutes.toFloat()) * canvasHeight)
                    drawLine(
                        color = thresholdColor,
                        start = Offset(0f, thresholdY),
                        end = Offset(canvasWidth, thresholdY),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )

                    // Draw baseline 0 line
                    drawLine(
                        color = outlineColor,
                        start = Offset(0f, canvasHeight),
                        end = Offset(canvasWidth, canvasHeight),
                        strokeWidth = 1.5f
                    )

                    val stepX = if (chartRecords.size > 1) {
                        canvasWidth / (chartRecords.size - 1)
                    } else {
                        canvasWidth / 2f
                    }

                    val points = mutableListOf<Offset>()
                    chartRecords.forEachIndexed { index, record ->
                        val x = if (chartRecords.size > 1) index * stepX else canvasWidth / 2f
                        val y = canvasHeight - ((record.latencyMinutes.toFloat() / maxMinutes.toFloat()) * canvasHeight)
                        points.add(Offset(x, y))
                    }

                    if (points.size > 1) {
                        val path = Path().apply {
                            moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = primaryColor,
                            style = Stroke(width = 3.5f)
                        )
                    }

                    // Draw data nodes
                    points.forEachIndexed { index, point ->
                        val record = chartRecords[index]
                        // Node circle
                        drawCircle(
                            color = if (record.latencyMinutes > 30) thresholdColor else primaryColor,
                            radius = 5.5f,
                            center = point
                        )
                        drawCircle(
                            color = Color(0xFF0F172A),
                            radius = 2.5f,
                            center = point
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = chartRecords.first().reportingDate.toString().drop(5),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${chartRecords.size} nights charted",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = chartRecords.last().reportingDate.toString().drop(5),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
