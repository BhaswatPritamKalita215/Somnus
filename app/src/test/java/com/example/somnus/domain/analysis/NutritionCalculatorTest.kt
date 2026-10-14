package com.example.somnus.domain.analysis

import com.example.somnus.domain.model.BarcodeFormat
import com.example.somnus.domain.model.DataCompleteness
import com.example.somnus.domain.model.FoodLogEntry
import com.example.somnus.domain.model.FoodProduct
import com.example.somnus.domain.model.MealType
import com.example.somnus.domain.model.NutritionBasis
import com.example.somnus.domain.model.ProductSource
import com.example.somnus.domain.model.ServingUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NutritionCalculatorTest {

    private val testPer100gProduct = FoodProduct(
        id = "test-prod-1",
        barcode = "1234567890128",
        barcodeFormat = BarcodeFormat.EAN_13,
        name = "Whole Grain Oats",
        brand = "Pure Brand",
        servingSize = 40.0,
        servingUnit = ServingUnit.GRAMS,
        nutritionBasis = NutritionBasis.PER_100_G,
        energyKcal = 370.0,
        proteinGrams = 13.0,
        carbohydratesGrams = 60.0,
        fatGrams = 7.0,
        fiberGrams = 10.0,
        sugarGrams = 1.0,
        sodiumMilligrams = 5.0,
        source = ProductSource.VERIFIED_CATALOGUE,
        completeness = DataCompleteness.COMPLETE
    )

    private val testPerServingProduct = FoodProduct(
        id = "test-prod-2",
        barcode = "9876543210123",
        barcodeFormat = BarcodeFormat.UPC_A,
        name = "Protein Bar",
        brand = "FitFoods",
        servingSize = 60.0,
        servingUnit = ServingUnit.SERVINGS,
        nutritionBasis = NutritionBasis.PER_SERVING,
        energyKcal = 220.0,
        proteinGrams = 20.0,
        carbohydratesGrams = 22.0,
        fatGrams = 6.0,
        fiberGrams = 5.0,
        sugarGrams = null, // Intentionally unmeasured
        sodiumMilligrams = 150.0,
        source = ProductSource.LOCAL_USER,
        completeness = DataCompleteness.PARTIAL
    )

    @Test
    fun computeRatio_per100g_gramsPortion() {
        // 50g of a per-100g product is ratio 0.5
        val ratio50g = NutritionCalculator.computeRatio(testPer100gProduct, 50.0, ServingUnit.GRAMS)
        assertEquals(0.5, ratio50g, 0.001)

        // 200g of a per-100g product is ratio 2.0
        val ratio200g = NutritionCalculator.computeRatio(testPer100gProduct, 200.0, ServingUnit.GRAMS)
        assertEquals(2.0, ratio200g, 0.001)
    }

    @Test
    fun computeRatio_per100g_servingsPortion() {
        // Serving size is 40g. 1 serving = 40g / 100g = 0.4 ratio
        val ratio1Serving = NutritionCalculator.computeRatio(testPer100gProduct, 1.0, ServingUnit.SERVINGS)
        assertEquals(0.4, ratio1Serving, 0.001)

        // 2.5 servings = 2.5 * 40g = 100g = 1.0 ratio
        val ratio2Point5Servings = NutritionCalculator.computeRatio(testPer100gProduct, 2.5, ServingUnit.SERVINGS)
        assertEquals(1.0, ratio2Point5Servings, 0.001)
    }

    @Test
    fun computeRatio_perServing_servingsAndGrams() {
        // Basis is per serving. 1 serving = 1.0 ratio
        val ratio1 = NutritionCalculator.computeRatio(testPerServingProduct, 1.0, ServingUnit.SERVINGS)
        assertEquals(1.0, ratio1, 0.001)

        // 2 servings = 2.0 ratio
        val ratio2 = NutritionCalculator.computeRatio(testPerServingProduct, 2.0, ServingUnit.SERVINGS)
        assertEquals(2.0, ratio2, 0.001)

        // Grams: serving size is 60g. Consuming 30g = 30 / 60 = 0.5 ratio
        val ratio30g = NutritionCalculator.computeRatio(testPerServingProduct, 30.0, ServingUnit.GRAMS)
        assertEquals(0.5, ratio30g, 0.001)
    }

    @Test
    fun calculatePortion_correctlyScalesNutrients() {
        // Consume 50g of oats (ratio 0.5)
        val snapshot = NutritionCalculator.calculatePortion(testPer100gProduct, 50.0, ServingUnit.GRAMS)

        assertEquals(185.0, snapshot.caloriesKcal, 0.1) // 370 * 0.5
        assertEquals(6.5, snapshot.proteinGrams!!, 0.01) // 13 * 0.5
        assertEquals(30.0, snapshot.carbohydratesGrams!!, 0.01) // 60 * 0.5
        assertEquals(3.5, snapshot.fatGrams!!, 0.01) // 7 * 0.5
        assertEquals(5.0, snapshot.fiberGrams!!, 0.01) // 10 * 0.5
        assertEquals(0.5, snapshot.sugarGrams!!, 0.01) // 1 * 0.5
        assertEquals(2.5, snapshot.sodiumMilligrams!!, 0.01) // 5 * 0.5
        assertEquals(1.25, snapshot.calculatedNumberOfServings!!, 0.01) // 50 / 40
    }

    @Test
    fun scientificHonesty_preservesNullNutrientsWithoutDefaultingToZero() {
        // testPerServingProduct has sugarGrams = null
        val snapshot = NutritionCalculator.calculatePortion(testPerServingProduct, 1.0, ServingUnit.SERVINGS)

        assertEquals(220.0, snapshot.caloriesKcal, 0.1)
        assertEquals(20.0, snapshot.proteinGrams!!, 0.01)
        assertNull("Unmeasured sugar must remain null and not become 0.0", snapshot.sugarGrams)
    }

    @Test
    fun scientificHonesty_distinguishesZeroFromNull() {
        val productWithZeroFat = testPer100gProduct.copy(fatGrams = 0.0, sugarGrams = null)
        val snapshot = NutritionCalculator.calculatePortion(productWithZeroFat, 100.0, ServingUnit.GRAMS)

        assertNotNull(snapshot.fatGrams)
        assertEquals(0.0, snapshot.fatGrams!!, 0.001)
        assertNull(snapshot.sugarGrams)
    }

    @Test
    fun createLogEntry_generatesImmutableSnapshot() {
        val entry = NutritionCalculator.createLogEntry(
            product = testPer100gProduct,
            quantity = 80.0,
            unit = ServingUnit.GRAMS,
            mealType = MealType.BREAKFAST
        )

        assertEquals("Whole Grain Oats", entry.productName)
        assertEquals(80.0, entry.quantity, 0.001)
        assertEquals(ServingUnit.GRAMS, entry.unit)
        assertEquals(MealType.BREAKFAST, entry.mealType)
        assertEquals(296.0, entry.calculatedCalories, 0.1) // 370 * 0.8
        assertEquals(10.4, entry.calculatedProtein!!, 0.01) // 13 * 0.8
        assertEquals(48.0, entry.calculatedCarbs!!, 0.01) // 60 * 0.8
        assertEquals(2.0, entry.numberOfServings!!, 0.01) // 80 / 40
    }

    @Test
    fun calculateDailySummary_correctlyAggregatesMeals() {
        val date = LocalDate.of(2026, 10, 14)
        val entry1 = NutritionCalculator.createLogEntry(
            product = testPer100gProduct,
            quantity = 50.0,
            unit = ServingUnit.GRAMS,
            mealType = MealType.BREAKFAST,
            localDate = date
        )
        val entry2 = NutritionCalculator.createLogEntry(
            product = testPerServingProduct,
            quantity = 1.0,
            unit = ServingUnit.SERVINGS,
            mealType = MealType.SNACK,
            localDate = date
        )

        val summary = NutritionCalculator.calculateDailySummary(
            date = date,
            entries = listOf(entry1, entry2),
            calorieTarget = 2000,
            activeCaloriesBurned = 350.0,
            totalCaloriesBurned = 2100.0
        )

        assertEquals(date, summary.date)
        assertEquals(405.0, summary.totalCalories, 0.1) // 185 + 220
        assertEquals(26.5, summary.totalProtein, 0.1) // 6.5 + 20.0
        assertEquals(52.0, summary.totalCarbs, 0.1) // 30.0 + 22.0
        assertEquals(9.5, summary.totalFat, 0.1) // 3.5 + 6.0
        assertEquals(2, summary.entries.size)
        assertEquals(2000, summary.calorieTarget)
        assertEquals(350.0, summary.activeCaloriesBurned!!, 0.1)
        assertEquals(2100.0, summary.totalCaloriesBurned!!, 0.1)
        assertTrue(summary.isLoggingComplete)
    }

    @Test
    fun evaluateCompleteness_classifiesCorrectly() {
        assertEquals(
            DataCompleteness.COMPLETE,
            NutritionCalculator.evaluateCompleteness(200.0, 10.0, 20.0, 5.0)
        )
        assertEquals(
            DataCompleteness.PARTIAL,
            NutritionCalculator.evaluateCompleteness(200.0, 10.0, null, 5.0)
        )
        assertEquals(
            DataCompleteness.MINIMAL,
            NutritionCalculator.evaluateCompleteness(200.0, null, null, null)
        )
    }
}
