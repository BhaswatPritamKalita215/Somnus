package com.example.somnus.ui.nutrition

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.somnus.domain.model.MealType
import com.example.somnus.ui.components.SomnusTopBar
import com.example.somnus.ui.scanner.BarcodeScannerView

/**
 * Screen displaying daily nutrition, food logs, calorie budget, macronutrient breakdown,
 * and entry points for barcode scanning and manual logging.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(
    viewModel: NutritionViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showGoalDialog by remember { mutableStateOf(false) }

    // Surface feedback messages in snackbar
    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissFeedback()
        }
    }

    // Fullscreen Barcode Scanner when in Scanning state
    if (uiState.scanState is ScanState.Scanning) {
        BarcodeScannerView(
            onBarcodeScanned = { barcode, format ->
                viewModel.onBarcodeDetected(barcode, format)
            },
            onDismiss = { viewModel.stopScanning() },
            onManualEntry = { viewModel.openManualEntry() }
        )
        return
    }

    Scaffold(
        topBar = {
            SomnusTopBar(
                title = "Nutrition & Diet",
                subtitle = "Scan-first calorie & nutrient tracking"
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.startScanning() },
                icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                text = { Text("Scan Food") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp, start = 16.dp, end = 16.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Date Navigator
                item {
                    DateNavigator(
                        selectedDate = uiState.selectedDate,
                        onPreviousDay = { viewModel.stepDate(-1) },
                        onNextDay = { viewModel.stepDate(1) },
                        onSelectToday = { viewModel.selectDate(java.time.LocalDate.now()) }
                    )
                }

                // 2. Calorie Budget & Energy Balance Card
                item {
                    CalorieBudgetCard(
                        loggedCalories = uiState.dailySummary.totalCalories.toInt(),
                        targetCalories = uiState.calorieTarget,
                        energyExpenditure = uiState.energyExpenditure,
                        onEditGoalClick = { showGoalDialog = true }
                    )
                }

                // 3. Macronutrient Breakdown Card
                item {
                    MacronutrientCard(
                        proteinGrams = uiState.dailySummary.totalProtein,
                        carbsGrams = uiState.dailySummary.totalCarbs,
                        fatGrams = uiState.dailySummary.totalFat,
                        totalCalories = uiState.dailySummary.totalCalories.toInt()
                    )
                }

                // 4. 7-Day Calorie Intake Chart
                item {
                    WeeklyCalorieChart(
                        summaries = uiState.weeklySummaries,
                        selectedDate = uiState.selectedDate,
                        targetCalories = uiState.calorieTarget,
                        onDaySelected = { date -> viewModel.selectDate(date) }
                    )
                }

                // 5. Meal Group Sections: Breakfast, Lunch, Dinner, Snack
                item {
                    Text(
                        text = "Daily Food Diary",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                val entriesByMeal = uiState.entries.groupBy { it.mealType }

                MealType.entries.forEach { mealType ->
                    item {
                        MealSectionCard(
                            mealType = mealType,
                            entries = entriesByMeal[mealType] ?: emptyList(),
                            onAddClick = { viewModel.openManualEntry(null, mealType) },
                            onDeleteEntry = { id -> viewModel.deleteLogEntry(id) }
                        )
                    }
                }
            }

            // Dialogs based on active ScanState
            when (val scan = uiState.scanState) {
                is ScanState.LookingUp -> {
                    LookingUpDialog(
                        barcode = scan.barcode,
                        onDismiss = { viewModel.dismissScanState() }
                    )
                }

                is ScanState.ProductFound -> {
                    ProductFoundDialog(
                        product = scan.product,
                        initialQuantity = scan.portionQuantity,
                        initialUnit = scan.portionUnit,
                        initialMealType = scan.mealType,
                        onConfirm = { quantity, unit, meal ->
                            viewModel.updateFoundProductPortion(quantity, unit)
                            viewModel.updateFoundProductMealType(meal)
                            viewModel.confirmFoundProductLog()
                        },
                        onDismiss = { viewModel.dismissScanState() }
                    )
                }

                is ScanState.ProductNotFound -> {
                    ProductNotFoundDialog(
                        barcode = scan.barcode,
                        onManualEntry = { code ->
                            viewModel.openManualEntry(code)
                        },
                        onDismiss = { viewModel.dismissScanState() }
                    )
                }

                is ScanState.ManualEntry -> {
                    ManualFoodDialog(
                        initialBarcode = scan.initialBarcode,
                        initialMealType = scan.initialMealType,
                        onSaveAndLog = { name, brand, barcode, servingVal, servingUnit, cal, prot, carb, fat, fiber, sugar, sodium, basis, portionVal, portionUnit, meal ->
                            viewModel.saveManualProductAndLog(
                                name = name,
                                brand = brand,
                                barcode = barcode,
                                servingSizeValue = servingVal,
                                servingSizeUnit = servingUnit,
                                calories = cal,
                                protein = prot,
                                carbs = carb,
                                fat = fat,
                                fiber = fiber,
                                sugar = sugar,
                                sodium = sodium,
                                basis = basis,
                                portionQuantity = portionVal,
                                portionUnit = portionUnit,
                                mealType = meal
                            )
                        },
                        onDismiss = { viewModel.dismissScanState() }
                    )
                }

                else -> {}
            }

            // Target Calorie Adjustment Dialog
            if (showGoalDialog) {
                CalorieTargetDialog(
                    currentTarget = uiState.calorieTarget,
                    onConfirm = { newTarget ->
                        viewModel.setDailyCalorieTarget(newTarget)
                        showGoalDialog = false
                    },
                    onDismiss = { showGoalDialog = false }
                )
            }
        }
    }
}
