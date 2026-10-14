package com.example.somnus.ui.analytics.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.somnus.domain.analytics.DaySleepSummary
import java.time.LocalDate

@Composable
fun SleepDurationTrendChart(
    summaries: List<DaySleepSummary>,
    averageDurationMinutes: Double?,
    startDate: LocalDate,
    endDate: LocalDate,
    onSelectDay: (DaySleepSummary) -> Unit,
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
                    text = "Sleep Duration Trend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (averageDurationMinutes != null) {
                    val h = (averageDurationMinutes / 60).toInt()
                    val m = (averageDurationMinutes % 60).toInt()
                    Text(
                        text = "Avg: ${h}h ${m}m",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Build full continuous calendar list of days
            val totalDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1
            val daysList = (0 until totalDays).map { offset ->
                val date = startDate.plusDays(offset)
                summaries.firstOrNull { it.sleepDate == date } ?: DaySleepSummary(
                    sleepDate = date,
                    totalSleepDurationMinutes = 0,
                    totalTimeInBedMinutes = 0,
                    bedtime = null,
                    wakeTime = null,
                    sessionCount = 0
                )
            }.takeLast(30) // Display up to 30 recent days for readability

            val maxHours = maxOf(10.0, (daysList.maxOfOrNull { it.totalSleepDurationMinutes / 60.0 } ?: 8.0) + 1.0)
            val primaryColor = MaterialTheme.colorScheme.primary
            val outlineColor = MaterialTheme.colorScheme.outline
            val missingBarColor = outlineColor.copy(alpha = 0.2f)
            val baselineColor = outlineColor.copy(alpha = 0.3f)
            val referenceLineColor = Color(0xFF38BDF8).copy(alpha = 0.5f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                ) {
                    val w = size.width
                    val h = size.height

                    // Draw average benchmark reference line
                    if (averageDurationMinutes != null && averageDurationMinutes > 0) {
                        val avgHours = averageDurationMinutes / 60.0
                        val avgY = h - ((avgHours / maxHours).toFloat() * h)
                        drawLine(
                            color = referenceLineColor,
                            start = Offset(0f, avgY),
                            end = Offset(w, avgY),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    }

                    // Draw baseline
                    drawLine(
                        color = baselineColor,
                        start = Offset(0f, h),
                        end = Offset(w, h),
                        strokeWidth = 1.5f
                    )

                    val barCount = daysList.size
                    val slotWidth = w / barCount
                    val barWidth = (slotWidth * 0.65f).coerceAtMost(24f)

                    daysList.forEachIndexed { index, day ->
                        val centerX = (index * slotWidth) + (slotWidth / 2f)
                        val durHours = day.totalSleepDurationMinutes / 60.0

                        if (durHours > 0.0) {
                            val barHeight = ((durHours / maxHours).toFloat() * h).coerceAtLeast(8f)
                            val topY = h - barHeight
                            drawRoundRect(
                                color = primaryColor,
                                topLeft = Offset(centerX - (barWidth / 2f), topY),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        } else {
                            // Missing night: subtle hollow marker at baseline
                            drawRoundRect(
                                color = missingBarColor,
                                topLeft = Offset(centerX - (barWidth / 2f), h - 6f),
                                size = Size(barWidth, 6f),
                                cornerRadius = CornerRadius(2f, 2f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = daysList.first().sleepDate.toString().drop(5),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(primaryColor, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Recorded", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.size(6.dp).background(missingBarColor, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Missing", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = daysList.last().sleepDate.toString().drop(5),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ScheduleTrendChart(
    summaries: List<DaySleepSummary>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Bedtime & Wake-Time Schedule",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Day-to-day consistency across midnight",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            val validNights = summaries.filter { it.bedtime != null && it.wakeTime != null }.takeLast(14)

            if (validNights.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Insufficient schedule observations",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val bedtimeColor = Color(0xFFA78BFA) // soft purple
                val wakeColor = Color(0xFFFBBF24)    // morning amber
                val midlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                val intervalColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    val w = size.width
                    val h = size.height

                    val stepX = if (validNights.size > 1) w / (validNights.size - 1) else w / 2f

                    // Midline separating bedtime region from wake-time region
                    drawLine(
                        color = midlineColor,
                        start = Offset(0f, h / 2f),
                        end = Offset(w, h / 2f),
                        strokeWidth = 1f
                    )

                    validNights.forEachIndexed { i, night ->
                        val x = if (validNights.size > 1) i * stepX else w / 2f

                        // Bedtime in upper half (scale 20:00 to 04:00)
                        val bed = night.bedtime!!
                        val bedMinutes = bed.hour * 60 + bed.minute
                        val normalizedBed = if (bedMinutes >= 1200) bedMinutes - 1200 else bedMinutes + 240
                        val bedY = (h * 0.45f) - ((normalizedBed / 480f) * (h * 0.4f)).coerceIn(0f, h * 0.4f)

                        // Wake time in lower half (scale 04:00 to 12:00)
                        val wake = night.wakeTime!!
                        val wakeMinutes = wake.hour * 60 + wake.minute
                        val normalizedWake = (wakeMinutes - 240).coerceAtLeast(0)
                        val wakeY = (h * 0.55f) + ((normalizedWake / 480f) * (h * 0.4f)).coerceIn(0f, h * 0.4f)

                        // Connecting vertical interval line
                        drawLine(
                            color = intervalColor,
                            start = Offset(x, bedY),
                            end = Offset(x, wakeY),
                            strokeWidth = 3f
                        )

                        // Bed node
                        drawCircle(color = bedtimeColor, radius = 5f, center = Offset(x, bedY))
                        // Wake node
                        drawCircle(color = wakeColor, radius = 5f, center = Offset(x, wakeY))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFFA78BFA), CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bedtime", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFFFBBF24), CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Wake Time", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = "${validNights.size} nights charted",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
