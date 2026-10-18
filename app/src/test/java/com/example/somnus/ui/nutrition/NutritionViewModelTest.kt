package com.example.somnus.ui.nutrition

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.somnus.data.healthconnect.FakeHealthConnectClientWrapper
import com.example.somnus.data.healthconnect.HealthConnectEnergyExpenditure
import com.example.somnus.data.healthconnect.HealthConnectManager
import com.example.somnus.data.local.SomnusDatabase
import com.example.somnus.data.network.FoodDatabaseClient
import com.example.somnus.data.preferences.UserPreferencesRepository
import com.example.somnus.data.repository.FoodLogRepositoryImpl
import com.example.somnus.data.repository.FoodProductRepositoryImpl
import com.example.somnus.data.repository.SleepSessionRepositoryImpl
import com.example.somnus.domain.model.BarcodeFormat
import com.example.somnus.domain.model.DataCompleteness
import com.example.somnus.domain.model.FoodProduct
import com.example.somnus.domain.model.MealType
import com.example.somnus.domain.model.NutritionBasis
import com.example.somnus.domain.model.ProductSource
import com.example.somnus.domain.model.ServingUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NutritionViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var db: SomnusDatabase
    private lateinit var productRepository: FoodProductRepositoryImpl
    private lateinit var logRepository: FoodLogRepositoryImpl
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var fakeClient: FakeHealthConnectClientWrapper
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var viewModel: NutritionViewModel

    private val sampleProduct = FoodProduct(
        id = "sample-1",
        barcode = "5000159461122",
        barcodeFormat = BarcodeFormat.EAN_13,
        name = "Snickers Bar",
        brand = "Mars",
        servingSize = 48.0,
        servingUnit = ServingUnit.GRAMS,
        nutritionBasis = NutritionBasis.PER_100_G,
        energyKcal = 488.0,
        proteinGrams = 8.6,
        carbohydratesGrams = 60.0,
        fatGrams = 24.0,
        source = ProductSource.OPEN_FOOD_FACTS,
        completeness = DataCompleteness.COMPLETE
    )

    private var remoteLookupCount = 0
    private val fakeRemoteClient = object : FoodDatabaseClient {
        override suspend fun lookupBarcode(barcode: String): Result<FoodProduct?> {
            remoteLookupCount++
            return if (barcode == sampleProduct.barcode) {
                Result.success(sampleProduct)
            } else {
                Result.success(null)
            }
        }
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = SomnusDatabase.buildInMemoryDatabase(context)

        productRepository = FoodProductRepositoryImpl(
            foodProductDao = db.foodProductDao(),
            remoteClient = fakeRemoteClient,
            ioDispatcher = testDispatcher
        )

        logRepository = FoodLogRepositoryImpl(
            foodLogDao = db.foodLogDao(),
            ioDispatcher = testDispatcher
        )

        val testDataStoreFile = File(tmpFolder.newFolder(), "test_user_prefs.preferences_pb")
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testDataStoreFile }
        )
        preferencesRepository = UserPreferencesRepository(testDataStore)

        fakeClient = FakeHealthConnectClientWrapper()
        fakeClient.energyPermissionsGranted = true
        fakeClient.energyExpenditureToReturn = HealthConnectEnergyExpenditure(
            activeCaloriesKcal = 420.0,
            totalCaloriesKcal = 2150.0
        )

        val sessionRepo = SleepSessionRepositoryImpl(db.sleepSessionDao(), testDispatcher)
        healthConnectManager = HealthConnectManager(
            clientWrapper = fakeClient,
            repository = sessionRepo,
            ioDispatcher = testDispatcher
        )

        viewModel = NutritionViewModel(
            foodProductRepository = productRepository,
            foodLogRepository = logRepository,
            userPreferencesRepository = preferencesRepository,
            healthConnectManager = healthConnectManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun initialState_hasDefaultCalorieTargetAndEmptyDiary() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LocalDate.now(), state.selectedDate)
        assertEquals(2000, state.calorieTarget)
        assertEquals(0, state.entries.size)
        assertEquals(0.0, state.dailySummary.totalCalories, 0.001)
        assertEquals(ScanState.Idle, state.scanState)

        collectJob.cancel()
    }

    @Test
    fun stepDate_updatesSelectedDateAndState() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.stepDate(-1)
        advanceUntilIdle()

        assertEquals(LocalDate.now().minusDays(1), viewModel.uiState.value.selectedDate)

        collectJob.cancel()
    }

    @Test
    fun barcodeDetected_whenOnlineLookupDisabledAndNotInCache_transitionsToPromptOnlineLookup_andNeverCallsRemote() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // Verify default toggle is OFF
        val prefs = preferencesRepository.userPreferencesFlow.first()
        assertFalse(prefs.onlineFoodLookupEnabled)
        assertEquals(0, remoteLookupCount)

        // Scan barcode not in local cache
        viewModel.onBarcodeDetected("5000159461122", BarcodeFormat.EAN_13)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected PromptOnlineLookup but was ${state.scanState}", state.scanState is ScanState.PromptOnlineLookup)
        assertEquals("5000159461122", (state.scanState as ScanState.PromptOnlineLookup).barcode)

        // Strict verification: OpenFoodFactsClient / remoteClient was NEVER called
        assertEquals(0, remoteLookupCount)

        collectJob.cancel()
    }

    @Test
    fun allowAndEnableOnlineLookup_enablesPreferenceAndFetchesProduct() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // 1. Initial scan with toggle OFF prompts dialog and does not call remote
        viewModel.onBarcodeDetected("5000159461122", BarcodeFormat.EAN_13)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.scanState is ScanState.PromptOnlineLookup)
        assertEquals(0, remoteLookupCount)

        // 2. User chooses "Allow and enable"
        viewModel.allowAndEnableOnlineLookup("5000159461122")
        advanceUntilIdle()

        // 3. Preference is updated to true in DataStore
        val updatedPrefs = preferencesRepository.userPreferencesFlow.first()
        assertTrue(updatedPrefs.onlineFoodLookupEnabled)

        // 4. Remote lookup was executed exactly once and product was found
        assertEquals(1, remoteLookupCount)
        val state = viewModel.uiState.value
        assertTrue("Expected ProductFound but was ${state.scanState}", state.scanState is ScanState.ProductFound)
        val found = state.scanState as ScanState.ProductFound
        assertEquals("Snickers Bar", found.product.name)

        collectJob.cancel()
    }

    @Test
    fun barcodeDetected_whenProductInLocalCache_findsLocallyWithoutRemoteCallEvenIfOnlineDisabled() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // Save product into local Room cache
        productRepository.saveProduct(sampleProduct)
        advanceUntilIdle()

        // Ensure online lookup is disabled
        assertFalse(preferencesRepository.userPreferencesFlow.first().onlineFoodLookupEnabled)
        assertEquals(0, remoteLookupCount)

        // Scan barcode that exists in local cache
        viewModel.onBarcodeDetected("5000159461122", BarcodeFormat.EAN_13)
        advanceUntilIdle()

        // Transitions directly to ProductFound without prompt and without calling remote
        val state = viewModel.uiState.value
        assertTrue(state.scanState is ScanState.ProductFound)
        assertEquals(0, remoteLookupCount)

        collectJob.cancel()
    }

    @Test
    fun barcodeDetected_productFound_transitionsToProductFoundState() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect {} }
        preferencesRepository.setOnlineFoodLookupEnabled(true)
        advanceUntilIdle()

        viewModel.onBarcodeDetected("5000159461122", BarcodeFormat.EAN_13)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected ScanState.ProductFound but got ${state.scanState}", state.scanState is ScanState.ProductFound)

        val found = state.scanState as ScanState.ProductFound
        assertEquals("Snickers Bar", found.product.name)
        assertEquals(48.0, found.portionQuantity, 0.01)

        collectJob.cancel()
    }

    @Test
    fun barcodeDetected_notFound_transitionsToNotFoundState() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect {} }
        preferencesRepository.setOnlineFoodLookupEnabled(true)
        advanceUntilIdle()

        viewModel.onBarcodeDetected("9999999999999", BarcodeFormat.EAN_13)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.scanState is ScanState.ProductNotFound)
        assertEquals("9999999999999", (state.scanState as ScanState.ProductNotFound).barcode)

        collectJob.cancel()
    }

    @Test
    fun confirmProductFound_logsFoodEntryAndUpdatesSummary() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect {} }
        preferencesRepository.setOnlineFoodLookupEnabled(true)
        advanceUntilIdle()

        // 1. Scan product
        viewModel.onBarcodeDetected("5000159461122", BarcodeFormat.EAN_13)
        advanceUntilIdle()

        // 2. Adjust portion to 50g
        viewModel.updateFoundProductPortion(50.0, ServingUnit.GRAMS)
        viewModel.updateFoundProductMealType(MealType.LUNCH)

        // 3. Confirm log
        viewModel.confirmFoundProductLog()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ScanState.Idle, state.scanState)
        assertEquals(1, state.entries.size)
        assertEquals("Snickers Bar", state.entries[0].productName)
        assertEquals(MealType.LUNCH, state.entries[0].mealType)
        assertEquals(244.0, state.entries[0].calculatedCalories, 0.1) // 488 * 0.5
        assertEquals(244.0, state.dailySummary.totalCalories, 0.1)
        assertNotNull(state.feedbackMessage)

        collectJob.cancel()
    }

    @Test
    fun manualFoodEntry_savesProductAndLogsEntry() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.saveManualProductAndLog(
            name = "Greek Yogurt",
            brand = "Chobani",
            barcode = "MANUAL_001",
            servingSizeValue = 150.0,
            servingSizeUnit = ServingUnit.GRAMS,
            calories = 130,
            protein = 15.0,
            carbs = 9.0,
            fat = 3.0,
            fiber = 0.0,
            sugar = 8.0,
            sodium = 50.0,
            basis = NutritionBasis.PER_100_G,
            portionQuantity = 150.0,
            portionUnit = ServingUnit.GRAMS,
            mealType = MealType.BREAKFAST
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.entries.size)
        assertEquals("Greek Yogurt", state.entries[0].productName)
        assertEquals(195.0, state.entries[0].calculatedCalories, 0.1) // 130 * 1.5 = 195
        assertEquals(22.5, state.dailySummary.totalProtein, 0.1) // 15 * 1.5 = 22.5

        collectJob.cancel()
    }
}
