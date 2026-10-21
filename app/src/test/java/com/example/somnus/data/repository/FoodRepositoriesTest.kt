package com.example.somnus.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.somnus.data.local.SomnusDatabase
import com.example.somnus.data.network.FoodDatabaseClient
import com.example.somnus.domain.analysis.NutritionCalculator
import com.example.somnus.domain.model.BarcodeFormat
import com.example.somnus.domain.model.DataCompleteness
import com.example.somnus.domain.model.FoodProduct
import com.example.somnus.domain.model.MealType
import com.example.somnus.domain.model.NutritionBasis
import com.example.somnus.domain.model.ProductSource
import com.example.somnus.domain.model.ServingUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FoodRepositoriesTest {

    private lateinit var db: SomnusDatabase
    private lateinit var productRepository: FoodProductRepository
    private lateinit var logRepository: FoodLogRepository

    private val fakeRemoteProduct = FoodProduct(
        id = "remote-1",
        barcode = "7622210449283",
        barcodeFormat = BarcodeFormat.EAN_13,
        name = "Oreo Cookies",
        brand = "Nabisco",
        servingSize = 34.0,
        servingUnit = ServingUnit.GRAMS,
        nutritionBasis = NutritionBasis.PER_100_G,
        energyKcal = 480.0,
        proteinGrams = 5.0,
        carbohydratesGrams = 68.0,
        fatGrams = 20.0,
        source = ProductSource.OPEN_FOOD_FACTS,
        completeness = DataCompleteness.COMPLETE
    )

    private var remoteCallCount = 0
    private val fakeRemoteClient = object : FoodDatabaseClient {
        override suspend fun lookupBarcode(barcode: String): Result<FoodProduct?> {
            remoteCallCount++
            return if (barcode == fakeRemoteProduct.barcode) {
                Result.success(fakeRemoteProduct)
            } else {
                Result.success(null)
            }
        }
    }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = SomnusDatabase.buildInMemoryDatabase(context)
        productRepository = FoodProductRepositoryImpl(
            foodProductDao = db.foodProductDao(),
            remoteClient = fakeRemoteClient
        )
        logRepository = FoodLogRepositoryImpl(
            foodLogDao = db.foodLogDao()
        )
        remoteCallCount = 0
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun lookupProduct_fallbackAndLocalCaching() = runBlocking {
        // 1. Initial lookup: not in local DB, calls remote client
        val result1 = productRepository.lookupWithRemoteFallback("7622210449283")
        assertTrue(result1.isSuccess)
        val product1 = result1.getOrNull()
        assertNotNull(product1)
        assertEquals("Oreo Cookies", product1!!.name)
        assertEquals(1, remoteCallCount)

        // 2. Second lookup: should be served from local Room DB, remoteClient is NOT called again
        val result2 = productRepository.lookupWithRemoteFallback("7622210449283")
        assertTrue(result2.isSuccess)
        val product2 = result2.getOrNull()
        assertNotNull(product2)
        assertEquals("Oreo Cookies", product2!!.name)
        assertEquals(1, remoteCallCount) // Call count remains 1!
    }

    @Test
    fun logFoodAndDailySummary_persistsAndAggregatesAccurately() = runBlocking {
        val today = LocalDate.of(2026, 10, 14)

        val entry1 = NutritionCalculator.createLogEntry(
            product = fakeRemoteProduct,
            quantity = 34.0, // 1 serving
            unit = ServingUnit.GRAMS,
            mealType = MealType.SNACK,
            localDate = today
        )

        logRepository.logFood(entry1)

        val entries = logRepository.getEntriesForDate(today).first()
        assertEquals(1, entries.size)
        assertEquals("Oreo Cookies", entries[0].productName)
        assertEquals(163.2, entries[0].calculatedCalories, 0.1) // 480 * (34 / 100) = 163.2

        val summary = logRepository.getDailySummary(today, 2000).first()
        assertEquals(163.2, summary.totalCalories, 0.1)
        assertEquals(1, summary.entries.size)
    }

    @Test
    fun deleteFoodEntry_removesEntry() = runBlocking {
        val today = LocalDate.now()
        val entry = NutritionCalculator.createLogEntry(
            product = fakeRemoteProduct,
            quantity = 50.0,
            unit = ServingUnit.GRAMS,
            localDate = today
        )

        logRepository.logFood(entry)
        assertEquals(1, logRepository.getEntriesForDate(today).first().size)

        logRepository.deleteEntry(entry.id)
        assertEquals(0, logRepository.getEntriesForDate(today).first().size)
    }

    @Test
    fun updateFoodEntry_updatesNutrientsAndPreservesId() = runBlocking {
        val today = LocalDate.now()
        val entry = NutritionCalculator.createLogEntry(
            product = fakeRemoteProduct,
            quantity = 34.0,
            unit = ServingUnit.GRAMS,
            localDate = today
        )

        logRepository.logFood(entry)
        val saved = logRepository.getEntryById(entry.id)
        assertNotNull(saved)
        assertEquals("Oreo Cookies", saved!!.productName)
        assertEquals(163.2, saved.calculatedCalories, 0.1)

        val updated = saved.copy(
            quantity = 68.0,
            calculatedCalories = 326.4,
            calculatedProtein = 3.4
        )
        logRepository.updateEntry(updated)

        val afterUpdate = logRepository.getEntryById(entry.id)
        assertNotNull(afterUpdate)
        assertEquals(68.0, afterUpdate!!.quantity, 0.01)
        assertEquals(326.4, afterUpdate.calculatedCalories, 0.1)
        assertEquals(3.4, afterUpdate.calculatedProtein ?: 0.0, 0.01)

        val dailySummary = logRepository.getDailySummary(today, 2000).first()
        assertEquals(326.4, dailySummary.totalCalories, 0.1)
    }
}
