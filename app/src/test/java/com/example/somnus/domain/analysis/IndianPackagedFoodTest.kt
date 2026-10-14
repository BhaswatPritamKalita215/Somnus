package com.example.somnus.domain.analysis

import com.example.somnus.domain.model.BarcodeFormat
import com.example.somnus.domain.model.FoodProduct
import com.example.somnus.domain.model.IndianFoodCategory
import com.example.somnus.domain.model.MealType
import com.example.somnus.domain.model.NutritionBasis
import com.example.somnus.domain.model.ProductSource
import com.example.somnus.domain.model.ServingUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class IndianPackagedFoodTest {

    private val parleG = FoodProduct(
        id = "parle-g-79g",
        barcode = "8901719101053",
        barcodeFormat = BarcodeFormat.EAN_13,
        name = "Parle-G Original Gluco Biscuits",
        nameHi = "पारले-जी ग्लूकोज बिस्कुट",
        brand = "Parle",
        brandAliases = listOf("Parle-G", "Parle G", "पारले जी"),
        category = IndianFoodCategory.BISCUITS_AND_COOKIES.displayName,
        packageDescription = "79.9g pack",
        netQuantityValue = 79.9,
        netQuantityUnit = ServingUnit.GRAMS,
        servingSize = 26.6, // FSSAI label states serving size is ~26.6g (approx 5 biscuits)
        servingUnit = ServingUnit.GRAMS,
        nutritionBasis = NutritionBasis.PER_100_G,
        energyKcal = 454.0,
        proteinGrams = 6.5,
        carbohydratesGrams = 78.2,
        sugarGrams = 25.5,
        addedSugarsGrams = 24.0,
        fatGrams = 13.0,
        saturatedFatGrams = 6.0,
        transFatGrams = 0.0,
        fiberGrams = 2.0,
        sodiumMilligrams = 280.0,
        saltGrams = 0.71,
        fssaiLicenseNumber = "10012022000122",
        source = ProductSource.VERIFIED_CATALOGUE
    )

    private val maggiNoodles = FoodProduct(
        id = "maggi-70g",
        barcode = "8901058852370",
        barcodeFormat = BarcodeFormat.EAN_13,
        name = "Maggi 2-Minute Masala Noodles",
        nameHi = "मैगी २-मिनट मसाला नूडल्स",
        brand = "Nestlé Maggi",
        brandAliases = listOf("Maggi", "मैगी", "Nestle Maggi"),
        category = IndianFoodCategory.NOODLES_AND_MEALS.displayName,
        packageDescription = "70g single pack",
        netQuantityValue = 70.0,
        netQuantityUnit = ServingUnit.GRAMS,
        servingSize = 70.0,
        servingUnit = ServingUnit.GRAMS,
        nutritionBasis = NutritionBasis.PER_100_G,
        energyKcal = 427.0,
        proteinGrams = 8.0,
        carbohydratesGrams = 63.5,
        sugarGrams = 2.2,
        addedSugarsGrams = 1.2,
        fatGrams = 15.7,
        saturatedFatGrams = 6.8,
        transFatGrams = 0.12,
        fiberGrams = 3.6,
        sodiumMilligrams = 1020.0,
        saltGrams = 2.59,
        fssaiLicenseNumber = "10012011000168",
        source = ProductSource.VERIFIED_CATALOGUE
    )

    private val haldiramAlooBhujia = FoodProduct(
        id = "haldiram-aloo-bhujia-150g",
        barcode = "8904063200109",
        barcodeFormat = BarcodeFormat.EAN_13,
        name = "Haldiram's Nagpur Aloo Bhujia",
        nameHi = "हल्दीराम आलू भुजिया",
        brand = "Haldiram's",
        brandAliases = listOf("Haldirams", "Haldiram's Nagpur"),
        category = IndianFoodCategory.NAMKEEN_AND_SAVOURY.displayName,
        packageDescription = "150g pouch",
        netQuantityValue = 150.0,
        netQuantityUnit = ServingUnit.GRAMS,
        servingSize = 30.0, // FSSAI recommended serving size for namkeen is 30g
        servingUnit = ServingUnit.GRAMS,
        nutritionBasis = NutritionBasis.PER_100_G,
        energyKcal = 588.0,
        proteinGrams = 8.4,
        carbohydratesGrams = 42.0,
        sugarGrams = 2.0,
        addedSugarsGrams = 0.0,
        fatGrams = 43.0,
        saturatedFatGrams = 14.5,
        transFatGrams = 0.0,
        fiberGrams = 4.2,
        sodiumMilligrams = 720.0,
        saltGrams = 1.83,
        fssaiLicenseNumber = "10012022000308",
        source = ProductSource.VERIFIED_CATALOGUE
    )

    private val amulButter = FoodProduct(
        id = "amul-butter-100g",
        barcode = "8901262010054",
        barcodeFormat = BarcodeFormat.EAN_13,
        name = "Amul Pasteurized Butter",
        nameHi = "अमूल मक्खन",
        brand = "Amul",
        brandAliases = listOf("The Taste of India", "Amul Butter"),
        category = IndianFoodCategory.DAIRY.displayName,
        packageDescription = "100g pack",
        netQuantityValue = 100.0,
        netQuantityUnit = ServingUnit.GRAMS,
        servingSize = 10.0,
        servingUnit = ServingUnit.GRAMS,
        nutritionBasis = NutritionBasis.PER_100_G,
        energyKcal = 722.0,
        proteinGrams = 0.6,
        carbohydratesGrams = 0.0,
        sugarGrams = 0.0,
        addedSugarsGrams = 0.0,
        fatGrams = 80.0,
        saturatedFatGrams = 51.0,
        transFatGrams = 3.6,
        fiberGrams = 0.0,
        sodiumMilligrams = 830.0,
        saltGrams = 2.1,
        fssaiLicenseNumber = "10012021000071",
        source = ProductSource.VERIFIED_CATALOGUE
    )

    private val kurkureMasalaMunch = FoodProduct(
        id = "kurkure-45g",
        barcode = "8901491101837",
        barcodeFormat = BarcodeFormat.EAN_13,
        name = "Kurkure Masala Munch",
        nameHi = "कुरकुरे मसाला मंच",
        brand = "Kurkure",
        brandAliases = listOf("PepsiCo", "Kurkure"),
        category = IndianFoodCategory.CHIPS_AND_CRISPS.displayName,
        packageDescription = "45g pack",
        netQuantityValue = 45.0,
        netQuantityUnit = ServingUnit.GRAMS,
        servingSize = 30.0,
        servingUnit = ServingUnit.GRAMS,
        nutritionBasis = NutritionBasis.PER_100_G,
        energyKcal = 559.0,
        proteinGrams = 5.8,
        carbohydratesGrams = 56.4,
        sugarGrams = 2.0,
        addedSugarsGrams = 1.0,
        fatGrams = 34.5,
        saturatedFatGrams = 15.6,
        transFatGrams = 0.1,
        sodiumMilligrams = 890.0,
        saltGrams = 2.26,
        source = ProductSource.VERIFIED_CATALOGUE
    )

    private val cadburyDairyMilk = FoodProduct(
        id = "cadbury-dairy-milk-24g",
        barcode = "8901233024884",
        barcodeFormat = BarcodeFormat.EAN_13,
        name = "Cadbury Dairy Milk Chocolate",
        nameHi = "कैडबरी डेयरी मिल्क",
        brand = "Cadbury",
        brandAliases = listOf("Mondelez", "Dairy Milk"),
        category = IndianFoodCategory.CHOCOLATE_AND_SWEETS.displayName,
        packageDescription = "24g bar",
        netQuantityValue = 24.0,
        netQuantityUnit = ServingUnit.GRAMS,
        servingSize = 12.0,
        servingUnit = ServingUnit.GRAMS,
        nutritionBasis = NutritionBasis.PER_100_G,
        energyKcal = 532.0,
        proteinGrams = 7.8,
        carbohydratesGrams = 60.5,
        sugarGrams = 57.0,
        addedSugarsGrams = 47.4,
        fatGrams = 29.8,
        saturatedFatGrams = 18.2,
        transFatGrams = 0.15,
        sodiumMilligrams = 135.0,
        saltGrams = 0.34,
        source = ProductSource.VERIFIED_CATALOGUE
    )

    private val ean8SmallPack = FoodProduct(
        id = "small-sachet-10g",
        barcode = "89012343", // 8-digit EAN-8 format
        barcodeFormat = BarcodeFormat.EAN_8,
        name = "Small Namkeen Sachet ₹5",
        nameHi = "छोटा नमकीन पाउच",
        brand = "Desi Bites",
        category = IndianFoodCategory.REGIONAL_SNACKS.displayName,
        packageDescription = "10g sachet",
        netQuantityValue = 10.0,
        netQuantityUnit = ServingUnit.GRAMS,
        nutritionBasis = NutritionBasis.PER_100_G,
        energyKcal = 520.0,
        proteinGrams = 6.0,
        carbohydratesGrams = 50.0,
        fatGrams = 32.0,
        source = ProductSource.LOCAL_USER
    )

    private val importedToblerone = FoodProduct(
        id = "toblerone-100g",
        barcode = "7622210449283", // Swiss EAN-13 (non-890)
        barcodeFormat = BarcodeFormat.EAN_13,
        name = "Toblerone Milk Chocolate with Honey & Almond Nougat",
        brand = "Toblerone",
        category = IndianFoodCategory.IMPORTED.displayName,
        packageDescription = "100g bar",
        netQuantityValue = 100.0,
        netQuantityUnit = ServingUnit.GRAMS,
        nutritionBasis = NutritionBasis.PER_100_G,
        energyKcal = 529.0,
        proteinGrams = 5.6,
        carbohydratesGrams = 61.0,
        fatGrams = 29.0,
        source = ProductSource.OPEN_FOOD_FACTS
    )

    @Test
    fun gs1IndiaPrefix_correctlyFlaggedWithoutDiscriminatingForeignProducts() {
        assertTrue(parleG.isGs1IndiaPrefix)
        assertTrue(maggiNoodles.isGs1IndiaPrefix)
        assertTrue(haldiramAlooBhujia.isGs1IndiaPrefix)
        assertTrue(amulButter.isGs1IndiaPrefix)
        assertTrue(kurkureMasalaMunch.isGs1IndiaPrefix)
        assertTrue(cadburyDairyMilk.isGs1IndiaPrefix)

        // Foreign products must not carry GS1 India prefix, yet remain perfectly valid
        assertFalse(importedToblerone.isGs1IndiaPrefix)
        assertEquals("7622210449283", importedToblerone.barcode)
    }

    @Test
    fun packageLevelMath_calculatesWholePackCorrectly() {
        // Consuming 1 full packet (79.9g) of Parle-G
        // 454 kcal per 100g -> 454 * (79.9 / 100) = 362.746 kcal
        val fullPack = NutritionCalculator.calculatePortion(parleG, 1.0, ServingUnit.PACKAGES)
        assertEquals(362.7, fullPack.caloriesKcal, 0.2)
        assertEquals(5.19, fullPack.proteinGrams!!, 0.05)
        assertEquals(62.48, fullPack.carbohydratesGrams!!, 0.1)
        assertEquals(19.18, fullPack.addedSugarsGrams!!, 0.1) // 24.0 * 0.799 = 19.176
        assertEquals(4.79, fullPack.saturatedFatGrams!!, 0.05) // 6.0 * 0.799 = 4.794
        assertEquals(0.57, fullPack.saltGrams!!, 0.02) // 0.71 * 0.799 = 0.567
    }

    @Test
    fun packageLevelMath_fractionalPackConsumption() {
        // Consuming half a pack (0.5 pack = 35g) of Maggi 2-Minute Noodles
        // 427 kcal per 100g -> 427 * (70 * 0.5 / 100) = 427 * 0.35 = 149.45 kcal
        val halfPack = NutritionCalculator.calculatePortion(maggiNoodles, 0.5, ServingUnit.PACKAGES)
        assertEquals(149.5, halfPack.caloriesKcal, 0.2)
        assertEquals(2.8, halfPack.proteinGrams!!, 0.05)
        assertEquals(22.23, halfPack.carbohydratesGrams!!, 0.1)
        assertEquals(2.38, halfPack.saturatedFatGrams!!, 0.05) // 6.8 * 0.35 = 2.38
        assertEquals(0.04, halfPack.transFatGrams!!, 0.01) // 0.12 * 0.35 = 0.042
        assertEquals(357.0, halfPack.sodiumMilligrams!!, 0.5) // 1020 * 0.35 = 357.0
        assertEquals(0.91, halfPack.saltGrams!!, 0.02) // 2.59 * 0.35 = 0.9065
    }

    @Test
    fun portionSizeDifferentFromNetPackageQuantity() {
        // 1 serving of Haldiram's Aloo Bhujia is 30g, but the net pack is 150g
        val oneServingByGrams = NutritionCalculator.calculatePortion(haldiramAlooBhujia, 30.0, ServingUnit.GRAMS)
        assertEquals(176.4, oneServingByGrams.caloriesKcal, 0.2) // 588 * 0.3 = 176.4
        assertEquals(2.52, oneServingByGrams.proteinGrams!!, 0.05)
        assertEquals(4.35, oneServingByGrams.saturatedFatGrams!!, 0.05) // 14.5 * 0.3 = 4.35
        assertEquals(216.0, oneServingByGrams.sodiumMilligrams!!, 0.5) // 720 * 0.3 = 216.0
        assertEquals(0.55, oneServingByGrams.saltGrams!!, 0.02) // 1.83 * 0.3 = 0.549

        // 1 full package consumption is 150g = 5 servings
        val fullPack = NutritionCalculator.calculatePortion(haldiramAlooBhujia, 1.0, ServingUnit.PACKAGES)
        assertEquals(882.0, fullPack.caloriesKcal, 0.5) // 588 * 1.5 = 882.0
        assertEquals(5.0, fullPack.calculatedNumberOfServings!!, 0.01) // 150 / 30 = 5.0
    }

    @Test
    fun fssaiSaltVsSodium_strictChemicalDistinction() {
        // FSSAI regulations strictly distinguish Salt (NaCl) in g from Sodium (Na) in mg.
        // Sodium to Salt: Salt (g) ≈ Sodium (mg) * 0.00254
        val calculatedSaltFromSodium = NutritionCalculator.sodiumMgToSaltGrams(haldiramAlooBhujia.sodiumMilligrams!!)
        assertEquals(1.83, calculatedSaltFromSodium, 0.05) // 720 * 0.00254 = 1.8288

        val calculatedSodiumFromSalt = NutritionCalculator.saltGramsToSodiumMg(haldiramAlooBhujia.saltGrams!!)
        assertEquals(720.5, calculatedSodiumFromSalt, 1.5) // 1.83 / 0.00254 = 720.47
    }

    @Test
    fun unmeasuredNutrients_neverFabricatedAsZero() {
        // Product missing specific FSSAI fields (e.g. ean8SmallPack has no saturatedFat or salt listed)
        val snapshot = NutritionCalculator.calculatePortion(ean8SmallPack, 10.0, ServingUnit.GRAMS)
        assertEquals(52.0, snapshot.caloriesKcal, 0.1)
        assertNull(snapshot.saturatedFatGrams)
        assertNull(snapshot.transFatGrams)
        assertNull(snapshot.addedSugarsGrams)
        assertNull(snapshot.saltGrams)
        assertNull(snapshot.sodiumMilligrams)
    }

    @Test
    fun createLogEntry_preservesIndianFssaiFieldsAndHindiName() {
        val entry = NutritionCalculator.createLogEntry(
            product = cadburyDairyMilk,
            quantity = 1.0,
            unit = ServingUnit.PACKAGES,
            mealType = MealType.SNACK,
            localDate = LocalDate.of(2026, 10, 14)
        )

        assertEquals("Cadbury Dairy Milk Chocolate", entry.productName)
        assertEquals("Cadbury", entry.brand)
        assertEquals(127.7, entry.calculatedCalories, 0.2) // 532 * 0.24 = 127.68
        assertEquals(11.38, entry.calculatedAddedSugars!!, 0.05) // 47.4 * 0.24 = 11.376
        assertEquals(4.37, entry.calculatedSaturatedFat!!, 0.05) // 18.2 * 0.24 = 4.368
        assertEquals(0.04, entry.calculatedTransFat!!, 0.01) // 0.15 * 0.24 = 0.036
        assertEquals(0.08, entry.calculatedSaltG!!, 0.01) // 0.34 * 0.24 = 0.0816
        assertEquals(2.0, entry.numberOfServings!!, 0.01) // 24g / 12g = 2.0 servings
    }
}
