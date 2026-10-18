package com.example.somnus.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.somnus.data.healthconnect.HealthConnectEnergyExpenditure
import com.example.somnus.data.healthconnect.HealthConnectManager
import com.example.somnus.data.preferences.UserPreferencesRepository
import com.example.somnus.data.repository.FoodLogRepository
import com.example.somnus.data.repository.FoodProductRepository
import com.example.somnus.domain.analysis.NutritionCalculator
import com.example.somnus.domain.model.BarcodeFormat
import com.example.somnus.domain.model.DailyNutritionSummary
import com.example.somnus.domain.model.DataCompleteness
import com.example.somnus.domain.model.FoodLogEntry
import com.example.somnus.domain.model.FoodProduct
import com.example.somnus.domain.model.MealType
import com.example.somnus.domain.model.NutritionBasis
import com.example.somnus.domain.model.ProductSource
import com.example.somnus.domain.model.ServingUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

sealed interface ScanState {
    data object Idle : ScanState
    data object Scanning : ScanState
    data class LookingUp(val barcode: String) : ScanState
    data class PromptOnlineLookup(val barcode: String) : ScanState
    data class ProductFound(
        val product: FoodProduct,
        val portionQuantity: Double,
        val portionUnit: ServingUnit,
        val mealType: MealType
    ) : ScanState
    data class ProductNotFound(val barcode: String) : ScanState
    data class ManualEntry(
        val initialBarcode: String? = null,
        val initialMealType: MealType = MealType.SNACK
    ) : ScanState
}

data class NutritionUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val entries: List<FoodLogEntry> = emptyList(),
    val dailySummary: DailyNutritionSummary = DailyNutritionSummary(
        date = LocalDate.now(),
        totalCalories = 0.0,
        totalProtein = 0.0,
        totalCarbs = 0.0,
        totalFat = 0.0,
        entries = emptyList()
    ),
    val calorieTarget: Int = 2000,
    val energyExpenditure: HealthConnectEnergyExpenditure? = null,
    val weeklySummaries: List<DailyNutritionSummary> = emptyList(),
    val scanState: ScanState = ScanState.Idle,
    val feedbackMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class NutritionViewModel(
    private val foodProductRepository: FoodProductRepository,
    private val foodLogRepository: FoodLogRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val healthConnectManager: HealthConnectManager? = null
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _energyExpenditure = MutableStateFlow<HealthConnectEnergyExpenditure?>(null)
    val energyExpenditure: StateFlow<HealthConnectEnergyExpenditure?> = _energyExpenditure.asStateFlow()

    private val currentEntriesFlow = _selectedDate.flatMapLatest { date ->
        foodLogRepository.getEntriesForDate(date)
    }

    private val weeklyEntriesFlow = _selectedDate.flatMapLatest { date ->
        val startDate = date.minusDays(6)
        foodLogRepository.getEntriesForDateRange(startDate, date)
    }

    private val calorieTargetFlow = userPreferencesRepository.userPreferencesFlow.map { it.dailyCalorieTarget }

    private data class FoodData(
        val date: LocalDate,
        val entries: List<FoodLogEntry>,
        val weeklySummaries: List<DailyNutritionSummary>,
        val target: Int
    )

    private data class InteractionData(
        val scan: ScanState,
        val feedback: String?,
        val energy: HealthConnectEnergyExpenditure?
    )

    private val foodDataFlow = combine(
        _selectedDate,
        currentEntriesFlow,
        weeklyEntriesFlow,
        calorieTargetFlow
    ) { date, entries, weeklyEntries, target ->
        val weeklySummaries = (0..6).map { dayOffset ->
            val day = date.minusDays((6 - dayOffset).toLong())
            val dayEntries = weeklyEntries.filter { it.localDate == day }
            NutritionCalculator.calculateDailySummary(
                date = day,
                entries = dayEntries,
                calorieTarget = target
            )
        }
        FoodData(date, entries, weeklySummaries, target)
    }

    private val interactionFlow = combine(
        _scanState,
        _feedbackMessage,
        _energyExpenditure
    ) { scan, feedback, energy ->
        InteractionData(scan, feedback, energy)
    }

    val uiState: StateFlow<NutritionUiState> = combine(
        foodDataFlow,
        interactionFlow
    ) { food, interaction ->
        val dailySummary = NutritionCalculator.calculateDailySummary(
            date = food.date,
            entries = food.entries,
            calorieTarget = food.target,
            activeCaloriesBurned = interaction.energy?.activeCaloriesKcal,
            totalCaloriesBurned = interaction.energy?.totalCaloriesKcal
        )

        NutritionUiState(
            selectedDate = food.date,
            entries = food.entries,
            dailySummary = dailySummary,
            calorieTarget = food.target,
            energyExpenditure = interaction.energy,
            weeklySummaries = food.weeklySummaries,
            scanState = interaction.scan,
            feedbackMessage = interaction.feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NutritionUiState()
    )

    init {
        loadEnergyExpenditureForSelectedDate()
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        loadEnergyExpenditureForSelectedDate()
    }

    fun stepDate(days: Long) {
        _selectedDate.update { it.plusDays(days) }
        loadEnergyExpenditureForSelectedDate()
    }

    fun setDailyCalorieTarget(target: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setDailyCalorieTarget(target)
        }
    }

    fun startScanning() {
        _scanState.value = ScanState.Scanning
    }

    fun stopScanning() {
        _scanState.value = ScanState.Idle
    }

    fun openManualEntry(barcode: String? = null, mealType: MealType = MealType.SNACK) {
        _scanState.value = ScanState.ManualEntry(barcode, mealType)
    }

    fun onBarcodeDetected(barcode: String, format: BarcodeFormat) {
        viewModelScope.launch {
            // Check local Room cache first
            val localProduct = foodProductRepository.findByBarcodeSync(barcode)
            if (localProduct != null) {
                val defaultUnit = localProduct.servingUnit
                val defaultQuantity = localProduct.servingSize ?: 100.0
                val defaultMeal = guessCurrentMealType()

                _scanState.value = ScanState.ProductFound(
                    product = localProduct,
                    portionQuantity = defaultQuantity,
                    portionUnit = defaultUnit,
                    mealType = defaultMeal
                )
                return@launch
            }

            // Not in local cache. Check if online lookup toggle is enabled in DataStore
            val isOnlineEnabled = userPreferencesRepository.userPreferencesFlow.first().onlineFoodLookupEnabled
            if (!isOnlineEnabled) {
                // With toggle OFF, never call OpenFoodFactsClient, prompt the user
                _scanState.value = ScanState.PromptOnlineLookup(barcode)
                return@launch
            }

            // Online lookup is enabled
            performOnlineLookup(barcode)
        }
    }

    fun allowAndEnableOnlineLookup(barcode: String) {
        viewModelScope.launch {
            userPreferencesRepository.setOnlineFoodLookupEnabled(true)
            performOnlineLookup(barcode)
        }
    }

    private suspend fun performOnlineLookup(barcode: String) {
        _scanState.value = ScanState.LookingUp(barcode)
        val result = foodProductRepository.lookupWithRemoteFallback(barcode, allowRemote = true)
        val product = result.getOrNull()
        if (product != null) {
            val defaultUnit = product.servingUnit
            val defaultQuantity = product.servingSize ?: 100.0
            val defaultMeal = guessCurrentMealType()

            _scanState.value = ScanState.ProductFound(
                product = product,
                portionQuantity = defaultQuantity,
                portionUnit = defaultUnit,
                mealType = defaultMeal
            )
        } else {
            _scanState.value = ScanState.ProductNotFound(barcode)
        }
    }

    fun updateFoundProductPortion(quantity: Double, unit: ServingUnit) {
        val current = _scanState.value
        if (current is ScanState.ProductFound) {
            _scanState.value = current.copy(
                portionQuantity = quantity.coerceAtLeast(0.1),
                portionUnit = unit
            )
        }
    }

    fun updateFoundProductMealType(mealType: MealType) {
        val current = _scanState.value
        if (current is ScanState.ProductFound) {
            _scanState.value = current.copy(mealType = mealType)
        }
    }

    fun confirmFoundProductLog() {
        val current = _scanState.value
        if (current is ScanState.ProductFound) {
            viewModelScope.launch {
                val entry = NutritionCalculator.createLogEntry(
                    product = current.product,
                    quantity = current.portionQuantity,
                    unit = current.portionUnit,
                    mealType = current.mealType,
                    localDate = _selectedDate.value
                )

                foodLogRepository.logFood(entry)
                _scanState.value = ScanState.Idle
                _feedbackMessage.value = "Logged ${entry.productName} (${entry.calculatedCalories.toInt()} kcal)"
            }
        }
    }

    fun saveManualProductAndLog(
        name: String,
        nameHi: String? = null,
        brand: String? = null,
        brandAliases: List<String> = emptyList(),
        category: String? = null,
        barcode: String? = null,
        netQuantityValue: Double? = null,
        netQuantityUnit: ServingUnit = ServingUnit.GRAMS,
        servingSizeValue: Double? = null,
        servingSizeUnit: ServingUnit? = null,
        calories: Int?,
        protein: Double? = null,
        carbs: Double? = null,
        fat: Double? = null,
        saturatedFat: Double? = null,
        transFat: Double? = null,
        fiber: Double? = null,
        sugar: Double? = null,
        addedSugars: Double? = null,
        sodium: Double? = null,
        salt: Double? = null,
        fssaiLicenseNumber: String? = null,
        basis: NutritionBasis = NutritionBasis.PER_100_G,
        portionQuantity: Double = 100.0,
        portionUnit: ServingUnit = ServingUnit.GRAMS,
        mealType: MealType = MealType.SNACK
    ) {
        viewModelScope.launch {
            val productBarcode = barcode?.takeIf { it.isNotBlank() } ?: "MANUAL_${UUID.randomUUID().toString().take(8)}"
            val energyVal = (calories ?: 0).toDouble()
            val completeness = NutritionCalculator.evaluateCompleteness(energyVal, protein, carbs, fat)

            val product = FoodProduct(
                id = UUID.randomUUID().toString(),
                barcode = productBarcode,
                barcodeFormat = com.example.somnus.domain.analysis.BarcodeValidator.detectFormat(productBarcode),
                name = name.trim(),
                nameHi = nameHi?.trim()?.takeIf { it.isNotEmpty() },
                brand = brand?.trim()?.takeIf { it.isNotEmpty() },
                brandAliases = brandAliases,
                category = category?.trim()?.takeIf { it.isNotEmpty() },
                netQuantityValue = netQuantityValue,
                netQuantityUnit = netQuantityUnit,
                servingSize = servingSizeValue,
                servingUnit = servingSizeUnit ?: ServingUnit.GRAMS,
                nutritionBasis = basis,
                energyKcal = energyVal,
                proteinGrams = protein,
                carbohydratesGrams = carbs,
                fatGrams = fat,
                saturatedFatGrams = saturatedFat,
                transFatGrams = transFat,
                fiberGrams = fiber,
                sugarGrams = sugar,
                addedSugarsGrams = addedSugars,
                sodiumMilligrams = sodium,
                saltGrams = salt,
                fssaiLicenseNumber = fssaiLicenseNumber?.trim()?.takeIf { it.isNotEmpty() },
                source = ProductSource.LOCAL_USER,
                completeness = completeness
            )

            // Save to local product catalog for future autocomplete/lookup
            foodProductRepository.saveProduct(product)

            val entry = NutritionCalculator.createLogEntry(
                product = product,
                quantity = portionQuantity,
                unit = portionUnit,
                mealType = mealType,
                localDate = _selectedDate.value
            )

            foodLogRepository.logFood(entry)
            _scanState.value = ScanState.Idle
            _feedbackMessage.value = "Saved and logged ${entry.productName}"
        }
    }

    fun deleteLogEntry(id: String) {
        viewModelScope.launch {
            foodLogRepository.deleteEntry(id)
            _feedbackMessage.value = "Food entry removed"
        }
    }

    fun dismissFeedback() {
        _feedbackMessage.value = null
    }

    fun dismissScanState() {
        _scanState.value = ScanState.Idle
    }

    private fun loadEnergyExpenditureForSelectedDate() {
        if (healthConnectManager == null) return
        viewModelScope.launch {
            try {
                if (healthConnectManager.hasEnergyExpenditurePermissions()) {
                    val zone = ZoneId.systemDefault()
                    val startOfDay = _selectedDate.value.atStartOfDay(zone).toInstant()
                    val endOfDay = _selectedDate.value.plusDays(1).atStartOfDay(zone).toInstant()
                    val result = healthConnectManager.getEnergyExpenditure(startOfDay, endOfDay)
                    _energyExpenditure.value = result
                }
            } catch (_: Exception) {
                _energyExpenditure.value = null
            }
        }
    }

    private fun guessCurrentMealType(): MealType {
        val hour = ZonedDateTime.now().hour
        return when (hour) {
            in 5..10 -> MealType.BREAKFAST
            in 11..14 -> MealType.LUNCH
            in 17..21 -> MealType.DINNER
            else -> MealType.SNACK
        }
    }

    companion object {
        fun provideFactory(
            foodProductRepository: FoodProductRepository,
            foodLogRepository: FoodLogRepository,
            userPreferencesRepository: UserPreferencesRepository,
            healthConnectManager: HealthConnectManager? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NutritionViewModel(
                    foodProductRepository = foodProductRepository,
                    foodLogRepository = foodLogRepository,
                    userPreferencesRepository = userPreferencesRepository,
                    healthConnectManager = healthConnectManager
                ) as T
            }
        }
    }
}
