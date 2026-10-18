package com.example.somnus.ui.nutrition

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.somnus.data.healthconnect.HealthConnectEnergyExpenditure
import com.example.somnus.domain.analysis.NutritionCalculator
import com.example.somnus.domain.model.DailyNutritionSummary
import com.example.somnus.domain.model.FoodLogEntry
import com.example.somnus.domain.model.FoodProduct
import com.example.somnus.domain.model.MealType
import com.example.somnus.domain.model.NutritionBasis
import com.example.somnus.domain.model.ProductSource
import com.example.somnus.domain.model.ServingUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Top date navigation bar allowing user to step through days.
 */
@Composable
fun DateNavigator(
    selectedDate: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onSelectToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isToday = selectedDate == LocalDate.now()
    val formatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
            Text(
                text = if (isToday) "Today" else selectedDate.format(formatter),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isToday) {
                Text(
                    text = selectedDate.format(formatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

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
    }
}

/**
 * Calorie budget and intake card.
 * Displays intake vs goal and explicitly segregates Health Connect energy expenditure.
 */
@Composable
fun CalorieBudgetCard(
    loggedCalories: Int,
    targetCalories: Int,
    energyExpenditure: HealthConnectEnergyExpenditure?,
    onEditGoalClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = (loggedCalories.toFloat() / targetCalories.toFloat()).coerceIn(0f, 1f)
    val remaining = targetCalories - loggedCalories

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Energy Intake",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onEditGoalClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Calorie Target",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "$loggedCalories",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Logged kcal",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (remaining >= 0) "$remaining kcal left" else "${-remaining} kcal over",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (remaining >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Target: $targetCalories kcal",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (loggedCalories > targetCalories) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface,
                strokeCap = StrokeCap.Round
            )

            // Health Connect Energy Expenditure Section (Strictly Segregated)
            if (energyExpenditure != null && (energyExpenditure.activeCaloriesKcal != null || energyExpenditure.totalCaloriesKcal != null)) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Health Connect Energy Expenditure",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (energyExpenditure.activeCaloriesKcal != null) {
                        Column {
                            Text(
                                text = "${energyExpenditure.activeCaloriesKcal.toInt()} kcal",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Active Burned",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (energyExpenditure.totalCaloriesKcal != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${energyExpenditure.totalCaloriesKcal.toInt()} kcal",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Total Expenditure (BMR+Active)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Dietary intake is logged by you. Energy expenditure is sourced from Health Connect and kept strictly segregated.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * Macronutrient summary card showing grams and calorie percentages.
 */
@Composable
fun MacronutrientCard(
    proteinGrams: Double,
    carbsGrams: Double,
    fatGrams: Double,
    totalCalories: Int,
    modifier: Modifier = Modifier
) {
    val proteinKcal = (proteinGrams * 4.0).toInt()
    val carbsKcal = (carbsGrams * 4.0).toInt()
    val fatKcal = (fatGrams * 9.0).toInt()
    val macroTotalKcal = (proteinKcal + carbsKcal + fatKcal).coerceAtLeast(1)

    val pPct = ((proteinKcal.toFloat() / macroTotalKcal) * 100).toInt()
    val cPct = ((carbsKcal.toFloat() / macroTotalKcal) * 100).toInt()
    val fPct = ((fatKcal.toFloat() / macroTotalKcal) * 100).toInt()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Macronutrients",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                MacroColumn(
                    label = "Protein",
                    grams = proteinGrams,
                    percent = if (totalCalories > 0) pPct else 0,
                    color = Color(0xFF4CAF50)
                )
                MacroColumn(
                    label = "Carbs",
                    grams = carbsGrams,
                    percent = if (totalCalories > 0) cPct else 0,
                    color = Color(0xFF2196F3)
                )
                MacroColumn(
                    label = "Fat",
                    grams = fatGrams,
                    percent = if (totalCalories > 0) fPct else 0,
                    color = Color(0xFFFF9800)
                )
            }
        }
    }
}

@Composable
private fun MacroColumn(
    label: String,
    grams: Double,
    percent: Int?,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = String.format(Locale.getDefault(), "%.1f g", grams),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
        if (percent != null) {
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Meal group section for Breakfast, Lunch, Dinner, or Snacks.
 */
@Composable
fun MealSectionCard(
    mealType: MealType,
    entries: List<FoodLogEntry>,
    onAddClick: () -> Unit,
    onDeleteEntry: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalCalories = entries.sumOf { it.calculatedCalories.toInt() }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = mealType.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$totalCalories kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(
                    onClick = onAddClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add to ${mealType.displayName}",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (entries.isEmpty()) {
                Text(
                    text = "No items logged yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                entries.forEach { entry ->
                    MealItemRow(
                        entry = entry,
                        onDelete = { onDeleteEntry(entry.id) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
fun MealItemRow(
    entry: FoodLogEntry,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val macroSummary = buildString {
        entry.calculatedProtein?.let { append("P: ${String.format(Locale.getDefault(), "%.1f", it)}g  ") }
        entry.calculatedCarbs?.let { append("C: ${String.format(Locale.getDefault(), "%.1f", it)}g  ") }
        entry.calculatedFat?.let { append("F: ${String.format(Locale.getDefault(), "%.1f", it)}g") }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.productName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.brand != null) {
                    Text(
                        text = "${entry.brand} • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${formatQuantity(entry.quantity)} ${entry.unit.symbol}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (macroSummary.isNotBlank()) {
                Text(
                    text = macroSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${entry.calculatedCalories.toInt()} kcal",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete item",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 7-Day bar chart visualizing daily calorie intake relative to target.
 */
@Composable
fun WeeklyCalorieChart(
    summaries: List<DailyNutritionSummary>,
    selectedDate: LocalDate,
    targetCalories: Int,
    onDaySelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    if (summaries.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "7-Day Calorie Intake",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            val primaryColor = MaterialTheme.colorScheme.primary
            val selectedColor = MaterialTheme.colorScheme.secondary
            val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
            val targetLineColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)

            val maxCal = maxOf(
                targetCalories,
                summaries.maxOfOrNull { it.totalCalories.toInt() } ?: 0
            ).coerceAtLeast(100)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val chartWidth = size.width
                val chartHeight = size.height - 24.dp.toPx()
                val barCount = summaries.size
                val barSpacing = chartWidth / barCount
                val barWidth = barSpacing * 0.5f

                // Draw target line
                val targetY = chartHeight * (1f - (targetCalories.toFloat() / maxCal.toFloat()))
                drawLine(
                    color = targetLineColor,
                    start = Offset(0f, targetY),
                    end = Offset(chartWidth, targetY),
                    strokeWidth = 2.dp.toPx()
                )

                summaries.forEachIndexed { index, summary ->
                    val isSelected = summary.date == selectedDate
                    val barHeight = chartHeight * (summary.totalCalories.toFloat() / maxCal.toFloat())
                    val barX = index * barSpacing + (barSpacing - barWidth) / 2f
                    val barY = chartHeight - barHeight

                    // Background track
                    drawRoundRect(
                        color = surfaceVariantColor.copy(alpha = 0.4f),
                        topLeft = Offset(barX, 0f),
                        size = Size(barWidth, chartHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )

                    // Filled bar
                    if (summary.totalCalories > 0) {
                        drawRoundRect(
                            color = if (isSelected) selectedColor else primaryColor,
                            topLeft = Offset(barX, barY),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
            }

            // Day labels below canvas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                val dayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
                summaries.forEach { summary ->
                    val isSelected = summary.date == selectedDate
                    Text(
                        text = summary.date.format(dayFormatter).take(2),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onDaySelected(summary.date) }
                    )
                }
            }
        }
    }
}

/**
 * Bottom Sheet / Dialog shown when a scanned product is identified.
 * Allows the user to select the consumed portion quantity, unit, and meal.
 */
@Composable
fun ProductFoundDialog(
    product: FoodProduct,
    initialQuantity: Double,
    initialUnit: ServingUnit,
    initialMealType: MealType,
    onConfirm: (quantity: Double, unit: ServingUnit, meal: MealType) -> Unit,
    onDismiss: () -> Unit
) {
    var quantity by remember { mutableDoubleStateOf(initialQuantity) }
    var quantityText by remember { mutableStateOf(formatQuantity(initialQuantity)) }
    var unit by remember { mutableStateOf(initialUnit) }
    var mealType by remember { mutableStateOf(initialMealType) }

    // Live preview snapshot
    val snapshot = remember(quantity, unit) {
        NutritionCalculator.calculatePortion(product, quantity, unit)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (product.nameHi != null) {
                    Text(
                        text = product.nameHi,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (product.brand != null) {
                    Text(
                        text = product.brand,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Badges: GS1 India (890), Category, Source, Completeness
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (product.isGs1IndiaPrefix) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "GS1 India (890)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    val sourceLabel = when (product.source) {
                        ProductSource.OPEN_FOOD_FACTS -> "Open Food Facts"
                        ProductSource.LOCAL_USER -> "Local Manual"
                        ProductSource.USER_VERIFIED -> "User Verified"
                        ProductSource.HEALTH_CONNECT -> "Health Connect"
                        ProductSource.VERIFIED_CATALOGUE -> "Verified DB"
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = sourceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (product.category != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = product.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = product.completeness.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (product.netQuantityValue != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Net Package: ${formatQuantity(product.netQuantityValue)} ${product.netQuantityUnit.symbol}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Meal selection chips
                Text(
                    text = "Meal",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MealType.entries.forEach { type ->
                        FilterChip(
                            selected = mealType == type,
                            onClick = { mealType = type },
                            label = { Text(type.displayName.take(3), fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Portion selection
                Text(
                    text = "Portion Consumed",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { str ->
                            quantityText = str
                            str.toDoubleOrNull()?.let {
                                if (it > 0) quantity = it
                            }
                        },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )

                    // Unit selector chips - including PACKAGES if net package quantity is known
                    val availableUnits = if (product.netQuantityValue != null) {
                        listOf(ServingUnit.GRAMS, ServingUnit.MILLILITERS, ServingUnit.SERVINGS, ServingUnit.PACKAGES)
                    } else {
                        listOf(ServingUnit.GRAMS, ServingUnit.MILLILITERS, ServingUnit.SERVINGS)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        availableUnits.forEach { u ->
                            FilterChip(
                                selected = unit == u,
                                onClick = {
                                    unit = u
                                    if (u == ServingUnit.PACKAGES && quantity > 10.0) {
                                        quantity = 1.0
                                        quantityText = "1"
                                    }
                                },
                                label = { Text(u.symbol) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Live calculated nutritional values
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Portion Nutrition Summary",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Calories:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${snapshot.caloriesKcal.toInt()} kcal",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Protein / Carbs / Fat:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${formatGrams(snapshot.proteinGrams)} / ${formatGrams(snapshot.carbohydratesGrams)} / ${formatGrams(snapshot.fatGrams)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // FSSAI Nutrients
                        if (snapshot.saturatedFatGrams != null || snapshot.transFatGrams != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Sat Fat / Trans Fat:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${formatGrams(snapshot.saturatedFatGrams)} / ${formatGrams(snapshot.transFatGrams)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        if (snapshot.sugarGrams != null || snapshot.addedSugarsGrams != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Sugar / Added Sugars:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${formatGrams(snapshot.sugarGrams)} / ${formatGrams(snapshot.addedSugarsGrams)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        if (snapshot.sodiumMilligrams != null || snapshot.saltGrams != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Sodium (mg) / Salt (g):",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${snapshot.sodiumMilligrams?.let { "${it.toInt()} mg" } ?: "—"} / ${snapshot.saltGrams?.let { "${it} g" } ?: "—"}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(quantity, unit, mealType) }
            ) {
                Text("Log to Diary")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog to manually enter a food item when not found via barcode scanner.
 * Fully supports Indian FSSAI mandatory fields (Added Sugars, Saturated Fat, Trans Fat, Salt vs Sodium).
 */
@Composable
fun ManualFoodDialog(
    initialBarcode: String?,
    initialMealType: MealType,
    onSaveAndLog: (
        name: String,
        nameHi: String?,
        brand: String?,
        brandAliases: List<String>,
        category: String?,
        barcode: String?,
        netQuantityValue: Double?,
        netQuantityUnit: ServingUnit,
        servingSizeValue: Double?,
        servingSizeUnit: ServingUnit?,
        calories: Int?,
        protein: Double?,
        carbs: Double?,
        fat: Double?,
        saturatedFat: Double?,
        transFat: Double?,
        fiber: Double?,
        sugar: Double?,
        addedSugars: Double?,
        sodium: Double?,
        salt: Double?,
        fssaiLicenseNumber: String?,
        basis: NutritionBasis,
        portionQuantity: Double,
        portionUnit: ServingUnit,
        mealType: MealType
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var nameHi by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf(initialBarcode ?: "") }
    var category by remember { mutableStateOf<String?>(null) }
    var netQuantityText by remember { mutableStateOf("") }
    var netQuantityUnit by remember { mutableStateOf(ServingUnit.GRAMS) }
    var caloriesText by remember { mutableStateOf("") }
    var proteinText by remember { mutableStateOf("") }
    var carbsText by remember { mutableStateOf("") }
    var fatText by remember { mutableStateOf("") }
    var saturatedFatText by remember { mutableStateOf("") }
    var transFatText by remember { mutableStateOf("") }
    var fiberText by remember { mutableStateOf("") }
    var sugarText by remember { mutableStateOf("") }
    var addedSugarsText by remember { mutableStateOf("") }
    var sodiumText by remember { mutableStateOf("") }
    var saltText by remember { mutableStateOf("") }
    var fssaiLicenseText by remember { mutableStateOf("") }
    var basis by remember { mutableStateOf(NutritionBasis.PER_100_G) }
    var portionText by remember { mutableStateOf("100") }
    var portionUnit by remember { mutableStateOf(ServingUnit.GRAMS) }
    var mealType by remember { mutableStateOf(initialMealType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manual Food Entry (India / FSSAI)") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Food Name * (English)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = nameHi,
                    onValueChange = { nameHi = it },
                    label = { Text("Name in Hindi / हिन्दी (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("Barcode / EAN (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Net package quantity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = netQuantityText,
                        onValueChange = { netQuantityText = it },
                        label = { Text("Net Pack Weight") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    Row {
                        listOf(ServingUnit.GRAMS, ServingUnit.MILLILITERS).forEach { u ->
                            FilterChip(
                                selected = netQuantityUnit == u,
                                onClick = { netQuantityUnit = u },
                                label = { Text(u.symbol) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Nutrition Label Basis",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = basis == NutritionBasis.PER_100_G,
                        onClick = { basis = NutritionBasis.PER_100_G },
                        label = { Text("Per 100g") }
                    )
                    FilterChip(
                        selected = basis == NutritionBasis.PER_100_ML,
                        onClick = { basis = NutritionBasis.PER_100_ML },
                        label = { Text("Per 100ml") }
                    )
                    FilterChip(
                        selected = basis == NutritionBasis.PER_SERVING,
                        onClick = { basis = NutritionBasis.PER_SERVING },
                        label = { Text("Per Serving") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = caloriesText,
                    onValueChange = { caloriesText = it },
                    label = { Text("Energy / Calories (kcal) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = proteinText,
                        onValueChange = { proteinText = it },
                        label = { Text("Protein (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = carbsText,
                        onValueChange = { carbsText = it },
                        label = { Text("Carbs (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fatText,
                        onValueChange = { fatText = it },
                        label = { Text("Fat (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // FSSAI Fats: Saturated & Trans Fat
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = saturatedFatText,
                        onValueChange = { saturatedFatText = it },
                        label = { Text("Sat Fat (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = transFatText,
                        onValueChange = { transFatText = it },
                        label = { Text("Trans Fat (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // FSSAI Sugars: Total & Added Sugars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = sugarText,
                        onValueChange = { sugarText = it },
                        label = { Text("Total Sugar (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = addedSugarsText,
                        onValueChange = { addedSugarsText = it },
                        label = { Text("Added Sugars (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // FSSAI Sodium vs Salt (Strictly distinct)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = sodiumText,
                        onValueChange = { sodiumText = it },
                        label = { Text("Sodium (mg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = saltText,
                        onValueChange = { saltText = it },
                        label = { Text("Salt (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = fssaiLicenseText,
                    onValueChange = { fssaiLicenseText = it },
                    label = { Text("FSSAI Lic. No. (Optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Portion Consumed Now",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = portionText,
                        onValueChange = { portionText = it },
                        label = { Text("Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    Row {
                        val portionUnits = if (netQuantityText.toDoubleOrNull() != null) {
                            listOf(ServingUnit.GRAMS, ServingUnit.MILLILITERS, ServingUnit.SERVINGS, ServingUnit.PACKAGES)
                        } else {
                            listOf(ServingUnit.GRAMS, ServingUnit.MILLILITERS, ServingUnit.SERVINGS)
                        }
                        portionUnits.forEach { u ->
                            FilterChip(
                                selected = portionUnit == u,
                                onClick = { portionUnit = u },
                                label = { Text(u.symbol) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val portion = portionText.toDoubleOrNull() ?: 100.0
                    val netQty = netQuantityText.toDoubleOrNull()
                    onSaveAndLog(
                        name,
                        nameHi.takeIf { it.isNotBlank() },
                        brand.takeIf { it.isNotBlank() },
                        emptyList(),
                        category,
                        barcode.takeIf { it.isNotBlank() },
                        netQty,
                        netQuantityUnit,
                        if (basis == NutritionBasis.PER_SERVING) 1.0 else (netQty ?: 100.0),
                        portionUnit,
                        caloriesText.toIntOrNull(),
                        proteinText.toDoubleOrNull(),
                        carbsText.toDoubleOrNull(),
                        fatText.toDoubleOrNull(),
                        saturatedFatText.toDoubleOrNull(),
                        transFatText.toDoubleOrNull(),
                        fiberText.toDoubleOrNull(),
                        sugarText.toDoubleOrNull(),
                        addedSugarsText.toDoubleOrNull(),
                        sodiumText.toDoubleOrNull(),
                        saltText.toDoubleOrNull(),
                        fssaiLicenseText.takeIf { it.isNotBlank() },
                        basis,
                        portion,
                        portionUnit,
                        mealType
                    )
                },
                enabled = name.isNotBlank() && caloriesText.toIntOrNull() != null
            ) {
                Text("Save & Log")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog shown when scanned barcode is not in local cache and online lookup is disabled.
 */
@Composable
fun PromptOnlineLookupDialog(
    barcode: String,
    onAllowAndEnable: () -> Unit,
    onManualEntry: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Online Food Lookup") },
        text = {
            Text("Look up this product on Open Food Facts? Only the barcode number is sent (your IP address is visible to their server).")
        },
        confirmButton = {
            Button(onClick = onAllowAndEnable) {
                Text("Allow and enable")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onManualEntry) {
                Text("Enter manually")
            }
        }
    )
}

/**
 * Loading dialog shown while querying local catalog and Open Food Facts.
 */
@Composable
fun LookingUpDialog(barcode: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Identifying Food") },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Looking up barcode:")
                    Text(
                        barcode,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog shown when product could not be identified automatically.
 */
@Composable
fun ProductNotFoundDialog(
    barcode: String,
    onManualEntry: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Product Not Found") },
        text = {
            Column {
                Text("Barcode $barcode was not found in the local catalogue or Open Food Facts database.")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Would you like to enter nutritional details manually?")
            }
        },
        confirmButton = {
            Button(onClick = { onManualEntry(barcode) }) {
                Text("Enter Manually")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog to adjust daily calorie target.
 */
@Composable
fun CalorieTargetDialog(
    currentTarget: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var targetText by remember { mutableStateOf(currentTarget.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Daily Calorie Target") },
        text = {
            Column {
                Text("Choose your daily energy target in kilocalories (kcal).")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = { Text("Target (kcal)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    targetText.toIntOrNull()?.let {
                        if (it in 500..10000) {
                            onConfirm(it)
                        }
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatQuantity(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", value)
    }
}

private fun formatGrams(value: Double?): String {
    return if (value != null) {
        String.format(Locale.getDefault(), "%.1fg", value)
    } else {
        "--"
    }
}
