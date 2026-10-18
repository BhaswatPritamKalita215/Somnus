package com.example.somnus.data.network

import com.example.somnus.BuildConfig
import com.example.somnus.domain.analysis.NutritionCalculator
import com.example.somnus.domain.model.BarcodeFormat
import com.example.somnus.domain.model.FoodProduct
import com.example.somnus.domain.model.NutritionBasis
import com.example.somnus.domain.model.ProductSource
import com.example.somnus.domain.model.ServingUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID

/**
 * Interface for remote barcode lookup. Decoupled for deterministic unit testing.
 */
interface FoodDatabaseClient {
    suspend fun lookupBarcode(barcode: String): Result<FoodProduct?>
}

/**
 * Production implementation of [FoodDatabaseClient] querying the public Open Food Facts API (v2).
 *
 * Privacy & Licensing Guarantees:
 * 1. Zero Personal Data: Only the numerical/string barcode is queried; no user diary data,
 *    identities, or locations are transmitted.
 * 2. Attribution: Attributed to Open Food Facts (ODbL license).
 * 3. Graceful Failure: Catches network disconnects, HTTP errors, and unlisted products cleanly.
 */
class OpenFoodFactsClient(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val baseUrl: String = "https://world.openfoodfacts.org/api/v2/product"
) : FoodDatabaseClient {

    companion object {
        private const val CONTACT = "https://github.com/BhaswatPritamKalita215/Somnus.git"
        val USER_AGENT = "SomnusAndroid/${BuildConfig.VERSION_NAME} ($CONTACT)"
        const val TIMEOUT_MS = 6000
    }

    override suspend fun lookupBarcode(barcode: String): Result<FoodProduct?> = withContext(ioDispatcher) {
        val sanitizedBarcode = barcode.trim()
        if (sanitizedBarcode.isBlank()) {
            return@withContext Result.success(null)
        }

        try {
            val url = URL("$baseUrl/$sanitizedBarcode.json")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                return@withContext Result.success(null)
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(
                    IllegalStateException("Open Food Facts returned HTTP $responseCode")
                )
            }

            val responseBody = connection.inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            }

            val parsedProduct = parseOpenFoodFactsJson(sanitizedBarcode, responseBody)
            Result.success(parsedProduct)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Pure JSON parser extracting product metadata and nutrition data from Open Food Facts API response.
     */
    fun parseOpenFoodFactsJson(barcode: String, jsonString: String): FoodProduct? {
        val root = JSONObject(jsonString)
        val status = root.optInt("status", -1)
        if (status != 1) return null

        val productObj = root.optJSONObject("product") ?: return null

        val productName = productObj.optString("product_name").ifBlank {
            productObj.optString("product_name_en").ifBlank {
                "Scanned Product ($barcode)"
            }
        }

        val nameHi = productObj.optString("product_name_hi").takeIf { it.isNotBlank() }

        val brands = productObj.optString("brands").takeIf { it.isNotBlank() }
        val brandAliases = mutableListOf<String>()
        val brandsTags = productObj.optJSONArray("brands_tags")
        if (brandsTags != null) {
            for (i in 0 until brandsTags.length()) {
                val tag = brandsTags.optString(i).trim()
                if (tag.isNotBlank() && !brandAliases.contains(tag)) {
                    brandAliases.add(tag)
                }
            }
        }
        if (brands != null && brandAliases.isEmpty()) {
            brandAliases.addAll(brands.split(",").map { it.trim() }.filter { it.isNotBlank() })
        }

        val categoriesTags = productObj.optJSONArray("categories_tags")
        val primaryCategory = if (categoriesTags != null && categoriesTags.length() > 0) {
            categoriesTags.optString(0).removePrefix("en:").replace("-", " ").trim()
        } else {
            productObj.optString("categories").split(",").firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
        }

        val genericName = productObj.optString("generic_name").takeIf { it.isNotBlank() }
        val packageDesc = productObj.optString("quantity").takeIf { it.isNotBlank() }

        // Net quantity parsing for package-level math
        var netQuantityValue: Double? = null
        var netQuantityUnit = ServingUnit.GRAMS

        if (packageDesc != null) {
            val match = Regex("""([0-9]+(?:\.[0-9]+)?)\s*([a-zA-Z]+)""").find(packageDesc)
            if (match != null) {
                val num = match.groupValues[1].toDoubleOrNull()
                val unitStr = match.groupValues[2].lowercase()
                when {
                    unitStr.contains("ml") -> {
                        netQuantityValue = num
                        netQuantityUnit = ServingUnit.MILLILITERS
                    }
                    unitStr == "l" || unitStr == "liter" || unitStr == "litre" -> {
                        netQuantityValue = num?.times(1000.0)
                        netQuantityUnit = ServingUnit.MILLILITERS
                    }
                    unitStr == "kg" -> {
                        netQuantityValue = num?.times(1000.0)
                        netQuantityUnit = ServingUnit.GRAMS
                    }
                    else -> {
                        netQuantityValue = num
                        netQuantityUnit = ServingUnit.GRAMS
                    }
                }
            }
        }
        if (netQuantityValue == null) {
            val productQuantityNum = productObj.optDouble("product_quantity", Double.NaN)
            if (!productQuantityNum.isNaN() && productQuantityNum > 0) {
                netQuantityValue = productQuantityNum
                val productQuantityUnitStr = productObj.optString("product_quantity_unit").lowercase()
                netQuantityUnit = if (productQuantityUnitStr.contains("ml") || productQuantityUnitStr.contains("l")) {
                    ServingUnit.MILLILITERS
                } else {
                    ServingUnit.GRAMS
                }
            }
        }

        // Serving size parsing
        var servingSize: Double? = null
        var servingUnit = ServingUnit.GRAMS

        val servingQuantityStr = productObj.optString("serving_quantity")
        if (servingQuantityStr.isNotBlank()) {
            servingSize = servingQuantityStr.toDoubleOrNull()
        }

        val servingSizeText = productObj.optString("serving_size")
        if (servingSize == null && servingSizeText.isNotBlank()) {
            val match = Regex("""([0-9]+(?:\.[0-9]+)?)""").find(servingSizeText)
            servingSize = match?.groupValues?.get(1)?.toDoubleOrNull()
        }

        if (servingSizeText.contains("ml", ignoreCase = true)) {
            servingUnit = ServingUnit.MILLILITERS
        }

        // Nutriments parsing
        val nutriments = productObj.optJSONObject("nutriments") ?: JSONObject()

        // Energy in kcal: Open Food Facts provides "energy-kcal_100g" or "energy-kcal_serving" or "energy-kcal"
        var kcal = nutriments.optDouble("energy-kcal_100g", Double.NaN)
        var basis = NutritionBasis.PER_100_G

        if (kcal.isNaN()) {
            kcal = nutriments.optDouble("energy-kcal", Double.NaN)
        }

        if (kcal.isNaN()) {
            val energyKj = nutriments.optDouble("energy_100g", Double.NaN)
            if (!energyKj.isNaN()) {
                kcal = energyKj / 4.184
            }
        }

        if (kcal.isNaN()) {
            val servingKcal = nutriments.optDouble("energy-kcal_serving", Double.NaN)
            if (!servingKcal.isNaN()) {
                kcal = servingKcal
                basis = NutritionBasis.PER_SERVING
            } else {
                kcal = 0.0
            }
        }

        val proteins = getNutrientOrNull(nutriments, "proteins_100g", "proteins")
        val carbs = getNutrientOrNull(nutriments, "carbohydrates_100g", "carbohydrates")
        val fat = getNutrientOrNull(nutriments, "fat_100g", "fat")
        val saturatedFat = getNutrientOrNull(nutriments, "saturated-fat_100g", "saturated-fat")
        val transFat = getNutrientOrNull(nutriments, "trans-fat_100g", "trans-fat")
        val fiber = getNutrientOrNull(nutriments, "fiber_100g", "fiber")
        val sugar = getNutrientOrNull(nutriments, "sugars_100g", "sugars")
        val addedSugars = getNutrientOrNull(nutriments, "added-sugars_100g", "added-sugars")
        val sodium = getNutrientOrNull(nutriments, "sodium_100g", "sodium")?.let { it * 1000.0 }
        val salt = getNutrientOrNull(nutriments, "salt_100g", "salt")

        val completeness = NutritionCalculator.evaluateCompleteness(
            calories = kcal,
            protein = proteins,
            carbs = carbs,
            fat = fat
        )

        return FoodProduct(
            id = UUID.randomUUID().toString(),
            barcode = barcode,
            barcodeFormat = inferBarcodeFormat(barcode),
            name = productName,
            nameHi = nameHi,
            brand = brands,
            brandAliases = brandAliases,
            category = primaryCategory,
            variant = genericName,
            packageDescription = packageDesc,
            netQuantityValue = netQuantityValue,
            netQuantityUnit = netQuantityUnit,
            servingSize = servingSize,
            servingUnit = servingUnit,
            nutritionBasis = basis,
            energyKcal = kcal,
            proteinGrams = proteins,
            carbohydratesGrams = carbs,
            sugarGrams = sugar,
            addedSugarsGrams = addedSugars,
            fatGrams = fat,
            saturatedFatGrams = saturatedFat,
            transFatGrams = transFat,
            fiberGrams = fiber,
            sodiumMilligrams = sodium,
            saltGrams = salt,
            source = ProductSource.OPEN_FOOD_FACTS,
            sourceProductId = barcode,
            lastVerifiedAt = Instant.now(),
            isUserModified = false,
            completeness = completeness
        )
    }

    private fun getNutrientOrNull(obj: JSONObject, primaryKey: String, fallbackKey: String): Double? {
        val v1 = obj.optDouble(primaryKey, Double.NaN)
        if (!v1.isNaN()) return v1
        val v2 = obj.optDouble(fallbackKey, Double.NaN)
        if (!v2.isNaN()) return v2
        return null
    }

    private fun inferBarcodeFormat(code: String): BarcodeFormat {
        val digitsOnly = code.all { it.isDigit() }
        return when {
            digitsOnly && code.length == 13 -> BarcodeFormat.EAN_13
            digitsOnly && code.length == 8 -> BarcodeFormat.EAN_8
            digitsOnly && code.length == 12 -> BarcodeFormat.UPC_A
            digitsOnly && (code.length == 6 || code.length == 7) -> BarcodeFormat.UPC_E
            else -> BarcodeFormat.OTHER
        }
    }
}
