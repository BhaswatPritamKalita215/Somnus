package com.example.somnus.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Standard barcode formats recognized by retail food packages.
 * Prioritizes EAN-13 (GTIN-13) and EAN-8 as primary Indian formats,
 * alongside UPC-A/UPC-E for imported products and QR codes.
 */
enum class BarcodeFormat(val displayName: String) {
    EAN_13("EAN-13"),
    EAN_8("EAN-8"),
    UPC_A("UPC-A"),
    UPC_E("UPC-E"),
    QR_CODE("QR Code"),
    OTHER("Other")
}

/**
 * Nutritional basis used on food labels.
 * Indian FSSAI regulations commonly mandate per-100g, per-100ml, and/or per-serving values.
 */
enum class NutritionBasis(val displayName: String) {
    PER_100_G("per 100 g"),
    PER_100_ML("per 100 ml"),
    PER_SERVING("per serving")
}

/**
 * Serving unit for product measurement and portion logging.
 */
enum class ServingUnit(val symbol: String, val displayName: String) {
    GRAMS("g", "Grams"),
    MILLILITERS("ml", "Milliliters"),
    PIECES("pc", "Pieces"),
    SERVINGS("serving", "Servings"),
    PACKAGES("pack", "Packets / Packs")
}

/**
 * Provenance of product nutritional data.
 */
enum class ProductSource(val displayName: String, val isUserGenerated: Boolean) {
    LOCAL_USER("User Created", true),
    USER_VERIFIED("Verified by User", true),
    OPEN_FOOD_FACTS("Open Food Facts (ODbL)", false),
    HEALTH_CONNECT("Health Connect", false),
    VERIFIED_CATALOGUE("Verified Database", false)
}

/**
 * Core packaged food categories commonly found in Indian retail.
 */
enum class IndianFoodCategory(val displayName: String, val displayNameHi: String) {
    BISCUITS_AND_COOKIES("Biscuits & Cookies", "बिस्कुट और कुकीज़"),
    NAMKEEN_AND_SAVOURY("Namkeen & Savoury Snacks", "नमकीन और भुजिया"),
    CHIPS_AND_CRISPS("Chips & Savoury Foods", "चिप्स और वेफर्स"),
    NOODLES_AND_MEALS("Instant Noodles & Ready Meals", "इंस्टेंट नूडल्स और भोजन"),
    CHOCOLATE_AND_SWEETS("Chocolates & Confectionery", "चॉकलेट और मिठाई"),
    BEVERAGES("Packaged Beverages", "पेय पदार्थ"),
    CEREALS_AND_BARS("Breakfast Cereals & Snack Bars", "सीरियल और स्नैक बार"),
    DAIRY("Packaged Dairy Products", "डेयरी उत्पाद"),
    REGIONAL_SNACKS("Regional Indian Snacks", "पारंपरिक स्नैक्स"),
    IMPORTED("Imported Foods", "आयातित उत्पाद"),
    OTHER("Other Packaged Food", "अन्य खाद्य उत्पाद")
}

/**
 * Level of nutritional information completeness.
 */
enum class DataCompleteness {
    COMPLETE, // Contains calories and all core macronutrients
    PARTIAL,  // Contains calories and at least one macronutrient
    MINIMAL   // Contains calories only or missing critical macros
}

/**
 * Meal category for grouping logged food intake.
 */
enum class MealType(val displayName: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack")
}

/**
 * Domain representation of a food product in the local catalogue.
 * Accommodates Indian FSSAI labeling mandates (Added sugars, Saturated Fat, Trans Fat, Salt vs Sodium).
 */
data class FoodProduct(
    val id: String,
    val barcode: String,
    val barcodeFormat: BarcodeFormat = BarcodeFormat.EAN_13,
    val name: String,
    val nameHi: String? = null,
    val brand: String? = null,
    val brandAliases: List<String> = emptyList(),
    val category: String? = null,
    val variant: String? = null,
    val packageDescription: String? = null,
    val netQuantityValue: Double? = null,
    val netQuantityUnit: ServingUnit = ServingUnit.GRAMS,
    val servingSize: Double? = null,
    val servingUnit: ServingUnit = ServingUnit.GRAMS,
    val nutritionBasis: NutritionBasis = NutritionBasis.PER_100_G,
    val energyKcal: Double,
    val proteinGrams: Double? = null,
    val carbohydratesGrams: Double? = null,
    val sugarGrams: Double? = null,
    val addedSugarsGrams: Double? = null,
    val fatGrams: Double? = null,
    val saturatedFatGrams: Double? = null,
    val transFatGrams: Double? = null,
    val fiberGrams: Double? = null,
    val sodiumMilligrams: Double? = null,
    val saltGrams: Double? = null,
    val fssaiLicenseNumber: String? = null,
    val source: ProductSource = ProductSource.LOCAL_USER,
    val sourceProductId: String? = null,
    val lastVerifiedAt: Instant = Instant.now(),
    val isUserModified: Boolean = false,
    val completeness: DataCompleteness = DataCompleteness.PARTIAL,
    val notes: String? = null
) {
    init {
        require(energyKcal >= 0.0) { "Energy (kcal) cannot be negative: $energyKcal" }
        require(barcode.isNotBlank()) { "Barcode cannot be blank" }
        require(name.isNotBlank()) { "Product name cannot be blank" }
    }

    /**
     * Checks if this product barcode carries the GS1 India prefix (890).
     * Informational only — never used as proof of manufacturing or to reject other valid prefixes.
     */
    val isGs1IndiaPrefix: Boolean
        get() = barcode.startsWith("890")
}

/**
 * Immutable record of a food consumed and logged by the user.
 * Preserves the exact nutritional snapshot at the time of consumption
 * so historical logs never silently change when product details are updated.
 */
data class FoodLogEntry(
    val id: String,
    val productId: String? = null,
    val barcode: String? = null,
    val productName: String,
    val brand: String? = null,
    val mealType: MealType = MealType.SNACK,
    val loggedAt: Instant = Instant.now(),
    val localDate: LocalDate = LocalDate.now(),
    val quantity: Double,
    val unit: ServingUnit,
    val numberOfServings: Double? = null,
    val calculatedCalories: Double,
    val calculatedProtein: Double? = null,
    val calculatedCarbs: Double? = null,
    val calculatedFat: Double? = null,
    val calculatedSaturatedFat: Double? = null,
    val calculatedTransFat: Double? = null,
    val calculatedSugar: Double? = null,
    val calculatedAddedSugars: Double? = null,
    val calculatedFiber: Double? = null,
    val calculatedSodiumMg: Double? = null,
    val calculatedSaltG: Double? = null,
    val source: ProductSource = ProductSource.LOCAL_USER,
    val externalRecordId: String? = null
) {
    init {
        require(quantity > 0.0) { "Logged quantity must be positive: $quantity" }
        require(calculatedCalories >= 0.0) { "Calculated calories cannot be negative: $calculatedCalories" }
        require(productName.isNotBlank()) { "Product name cannot be blank" }
    }
}

/**
 * Daily nutrition aggregate comparing intake against user target and energy expenditure.
 */
data class DailyNutritionSummary(
    val date: LocalDate,
    val totalCalories: Double,
    val totalProtein: Double,
    val totalCarbs: Double,
    val totalFat: Double,
    val entries: List<FoodLogEntry>,
    val calorieTarget: Int? = 2000,
    val activeCaloriesBurned: Double? = null,
    val totalCaloriesBurned: Double? = null,
    val isLoggingComplete: Boolean = entries.isNotEmpty()
) {
    val calorieProgressPercentage: Float
        get() {
            val target = calorieTarget ?: return 0f
            if (target <= 0) return 0f
            return ((totalCalories.toFloat() / target.toFloat()) * 100f).coerceIn(0f, 500f)
        }
}
