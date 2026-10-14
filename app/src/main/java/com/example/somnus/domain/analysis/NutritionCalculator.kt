package com.example.somnus.domain.analysis

import com.example.somnus.domain.model.DailyNutritionSummary
import com.example.somnus.domain.model.DataCompleteness
import com.example.somnus.domain.model.FoodLogEntry
import com.example.somnus.domain.model.FoodProduct
import com.example.somnus.domain.model.MealType
import com.example.somnus.domain.model.NutritionBasis
import com.example.somnus.domain.model.ServingUnit
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Calculated nutritional snapshot for a consumed portion of food.
 * Supports Indian FSSAI labeling mandates (Added sugars, Saturated Fat, Trans Fat, Salt vs Sodium).
 */
data class NutritionSnapshot(
    val caloriesKcal: Double,
    val proteinGrams: Double?,
    val carbohydratesGrams: Double?,
    val fatGrams: Double?,
    val saturatedFatGrams: Double?,
    val transFatGrams: Double?,
    val sugarGrams: Double?,
    val addedSugarsGrams: Double?,
    val fiberGrams: Double?,
    val sodiumMilligrams: Double?,
    val saltGrams: Double?,
    val effectiveRatio: Double,
    val calculatedNumberOfServings: Double?
)

/**
 * Pure domain calculator for quantity-based nutrition conversion and log entry generation.
 *
 * Guarantees:
 * 1. Unit awareness: Correctly scales per-100g, per-100ml, per-serving, and package-based label data.
 * 2. Scientific honesty: Distinguishes measured 0.0 from unmeasured (null) nutrients.
 *    Never assumes an unlisted nutrient is zero.
 * 3. Salt vs Sodium Integrity: Treats Salt (NaCl) and Sodium (Na) as chemically distinct metrics.
 *    Never confuses them or performs lossy implicit conversions.
 * 4. Immutable snapshotting: Generates self-contained [FoodLogEntry] records that remain
 *    invariant even if the catalog product is edited later.
 */
object NutritionCalculator {

    /**
     * Calculates the nutritional values for a given [quantity] and [unit] of a [product].
     */
    fun calculatePortion(
        product: FoodProduct,
        quantity: Double,
        unit: ServingUnit
    ): NutritionSnapshot {
        require(quantity > 0.0) { "Portion quantity must be strictly positive: $quantity" }

        val ratio = computeRatio(product, quantity, unit)
        val servings = computeServings(product, quantity, unit)

        return NutritionSnapshot(
            caloriesKcal = roundToDecimals(product.energyKcal * ratio, 1),
            proteinGrams = product.proteinGrams?.let { roundToDecimals(it * ratio, 2) },
            carbohydratesGrams = product.carbohydratesGrams?.let { roundToDecimals(it * ratio, 2) },
            fatGrams = product.fatGrams?.let { roundToDecimals(it * ratio, 2) },
            saturatedFatGrams = product.saturatedFatGrams?.let { roundToDecimals(it * ratio, 2) },
            transFatGrams = product.transFatGrams?.let { roundToDecimals(it * ratio, 2) },
            sugarGrams = product.sugarGrams?.let { roundToDecimals(it * ratio, 2) },
            addedSugarsGrams = product.addedSugarsGrams?.let { roundToDecimals(it * ratio, 2) },
            fiberGrams = product.fiberGrams?.let { roundToDecimals(it * ratio, 2) },
            sodiumMilligrams = product.sodiumMilligrams?.let { roundToDecimals(it * ratio, 1) },
            saltGrams = product.saltGrams?.let { roundToDecimals(it * ratio, 3) },
            effectiveRatio = ratio,
            calculatedNumberOfServings = servings
        )
    }

    /**
     * Creates an immutable [FoodLogEntry] snapshot from a product and consumed portion.
     */
    fun createLogEntry(
        product: FoodProduct,
        quantity: Double,
        unit: ServingUnit,
        mealType: MealType = MealType.SNACK,
        loggedAt: Instant = Instant.now(),
        localDate: LocalDate = LocalDate.now(),
        id: String = UUID.randomUUID().toString()
    ): FoodLogEntry {
        val portion = calculatePortion(product, quantity, unit)

        return FoodLogEntry(
            id = id,
            productId = product.id,
            barcode = product.barcode,
            productName = product.name,
            brand = product.brand,
            mealType = mealType,
            loggedAt = loggedAt,
            localDate = localDate,
            quantity = quantity,
            unit = unit,
            numberOfServings = portion.calculatedNumberOfServings,
            calculatedCalories = portion.caloriesKcal,
            calculatedProtein = portion.proteinGrams,
            calculatedCarbs = portion.carbohydratesGrams,
            calculatedFat = portion.fatGrams,
            calculatedSaturatedFat = portion.saturatedFatGrams,
            calculatedTransFat = portion.transFatGrams,
            calculatedFiber = portion.fiberGrams,
            calculatedSugar = portion.sugarGrams,
            calculatedAddedSugars = portion.addedSugarsGrams,
            calculatedSodiumMg = portion.sodiumMilligrams,
            calculatedSaltG = portion.saltGrams,
            source = product.source
        )
    }

    /**
     * Determines the multiplier ratio between the specified portion and the product's nutritional basis.
     * Accurately supports package-level calculations using net package weight/volume where available.
     */
    fun computeRatio(
        product: FoodProduct,
        quantity: Double,
        unit: ServingUnit
    ): Double {
        return when (product.nutritionBasis) {
            NutritionBasis.PER_100_G -> {
                when (unit) {
                    ServingUnit.GRAMS -> quantity / 100.0
                    ServingUnit.MILLILITERS -> quantity / 100.0
                    ServingUnit.SERVINGS, ServingUnit.PIECES -> {
                        val servingSize = product.servingSize ?: 100.0
                        (quantity * servingSize) / 100.0
                    }
                    ServingUnit.PACKAGES -> {
                        // Use net package quantity if available; fall back to servingSize or 100g
                        val netQuantity = product.netQuantityValue ?: product.servingSize ?: 100.0
                        (quantity * netQuantity) / 100.0
                    }
                }
            }
            NutritionBasis.PER_100_ML -> {
                when (unit) {
                    ServingUnit.MILLILITERS, ServingUnit.GRAMS -> quantity / 100.0
                    ServingUnit.SERVINGS, ServingUnit.PIECES -> {
                        val servingSize = product.servingSize ?: 100.0
                        (quantity * servingSize) / 100.0
                    }
                    ServingUnit.PACKAGES -> {
                        val netQuantity = product.netQuantityValue ?: product.servingSize ?: 100.0
                        (quantity * netQuantity) / 100.0
                    }
                }
            }
            NutritionBasis.PER_SERVING -> {
                when (unit) {
                    ServingUnit.SERVINGS, ServingUnit.PIECES -> quantity
                    ServingUnit.PACKAGES -> {
                        val netQuantity = product.netQuantityValue
                        val servingSize = product.servingSize
                        if (netQuantity != null && servingSize != null && servingSize > 0.0) {
                            quantity * (netQuantity / servingSize)
                        } else {
                            quantity
                        }
                    }
                    ServingUnit.GRAMS, ServingUnit.MILLILITERS -> {
                        val servingSize = product.servingSize
                        if (servingSize != null && servingSize > 0.0) {
                            quantity / servingSize
                        } else {
                            quantity / 100.0
                        }
                    }
                }
            }
        }
    }

    /**
     * Computes the number of official servings represented by the consumed quantity.
     */
    fun computeServings(
        product: FoodProduct,
        quantity: Double,
        unit: ServingUnit
    ): Double? {
        if (unit == ServingUnit.SERVINGS) return quantity
        val servingSize = product.servingSize ?: return null
        if (servingSize <= 0.0) return null

        return when (unit) {
            ServingUnit.GRAMS, ServingUnit.MILLILITERS -> roundToDecimals(quantity / servingSize, 2)
            ServingUnit.PIECES -> quantity
            ServingUnit.SERVINGS -> quantity
            ServingUnit.PACKAGES -> {
                val netQuantity = product.netQuantityValue
                if (netQuantity != null && netQuantity > 0.0) {
                    roundToDecimals(quantity * (netQuantity / servingSize), 2)
                } else {
                    quantity
                }
            }
        }
    }

    /**
     * Converts Sodium in milligrams to equivalent Salt in grams (NaCl).
     * Formula: Salt (g) = Sodium (mg) * 0.00254 (approximately 2.5x).
     * Note: Use only when explicit valid chemical conversion is requested.
     */
    fun sodiumMgToSaltGrams(sodiumMg: Double): Double {
        return roundToDecimals(sodiumMg * 0.00254, 3)
    }

    /**
     * Converts Salt in grams to equivalent Sodium in milligrams.
     * Formula: Sodium (mg) = Salt (g) / 0.00254 (approximately Salt (g) * 393.4).
     */
    fun saltGramsToSodiumMg(saltGrams: Double): Double {
        return roundToDecimals(saltGrams / 0.00254, 1)
    }

    /**
     * Evaluates data completeness based on available macronutrients.
     */
    fun evaluateCompleteness(
        calories: Double,
        protein: Double?,
        carbs: Double?,
        fat: Double?
    ): DataCompleteness {
        val hasProtein = protein != null
        val hasCarbs = carbs != null
        val hasFat = fat != null

        return when {
            hasProtein && hasCarbs && hasFat -> DataCompleteness.COMPLETE
            hasProtein || hasCarbs || hasFat -> DataCompleteness.PARTIAL
            else -> DataCompleteness.MINIMAL
        }
    }

    /**
     * Aggregates a list of [FoodLogEntry] records into a [DailyNutritionSummary].
     */
    fun calculateDailySummary(
        date: LocalDate,
        entries: List<FoodLogEntry>,
        calorieTarget: Int? = 2000,
        activeCaloriesBurned: Double? = null,
        totalCaloriesBurned: Double? = null
    ): DailyNutritionSummary {
        val totalCalories = entries.sumOf { it.calculatedCalories }
        val totalProtein = entries.mapNotNull { it.calculatedProtein }.sum()
        val totalCarbs = entries.mapNotNull { it.calculatedCarbs }.sum()
        val totalFat = entries.mapNotNull { it.calculatedFat }.sum()

        return DailyNutritionSummary(
            date = date,
            totalCalories = roundToDecimals(totalCalories, 1),
            totalProtein = roundToDecimals(totalProtein, 1),
            totalCarbs = roundToDecimals(totalCarbs, 1),
            totalFat = roundToDecimals(totalFat, 1),
            entries = entries,
            calorieTarget = calorieTarget,
            activeCaloriesBurned = activeCaloriesBurned,
            totalCaloriesBurned = totalCaloriesBurned
        )
    }

    private fun roundToDecimals(value: Double, decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10.0 }
        return kotlin.math.round(value * multiplier) / multiplier
    }
}
