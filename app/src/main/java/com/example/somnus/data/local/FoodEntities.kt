package com.example.somnus.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.somnus.domain.model.BarcodeFormat
import com.example.somnus.domain.model.DataCompleteness
import com.example.somnus.domain.model.FoodLogEntry
import com.example.somnus.domain.model.FoodProduct
import com.example.somnus.domain.model.MealType
import com.example.somnus.domain.model.NutritionBasis
import com.example.somnus.domain.model.ProductSource
import com.example.somnus.domain.model.ServingUnit
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "food_products",
    indices = [
        Index(value = ["barcode"], unique = false),
        Index(value = ["name"]),
        Index(value = ["nameHi"]),
        Index(value = ["category"])
    ]
)
data class FoodProductEntity(
    @PrimaryKey
    val id: String,
    val barcode: String,
    val barcodeFormat: String,
    val name: String,
    val nameHi: String? = null,
    val brand: String? = null,
    val brandAliasesJson: String = "[]",
    val category: String? = null,
    val variant: String? = null,
    val packageDescription: String? = null,
    val netQuantityValue: Double? = null,
    val netQuantityUnit: String = "GRAMS",
    val servingSize: Double? = null,
    val servingUnit: String = "GRAMS",
    val nutritionBasis: String = "PER_100_G",
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
    val source: String = "LOCAL_USER",
    val sourceProductId: String? = null,
    val lastVerifiedAt: Long,
    val isUserModified: Boolean = false,
    val completeness: String = "PARTIAL",
    val notes: String? = null
) {
    fun toDomain(): FoodProduct {
        val aliases = try {
            val jsonArr = JSONArray(brandAliasesJson)
            (0 until jsonArr.length()).map { jsonArr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }

        return FoodProduct(
            id = id,
            barcode = barcode,
            barcodeFormat = try {
                BarcodeFormat.valueOf(barcodeFormat)
            } catch (e: Exception) {
                BarcodeFormat.OTHER
            },
            name = name,
            nameHi = nameHi,
            brand = brand,
            brandAliases = aliases,
            category = category,
            variant = variant,
            packageDescription = packageDescription,
            netQuantityValue = netQuantityValue,
            netQuantityUnit = try {
                ServingUnit.valueOf(netQuantityUnit)
            } catch (e: Exception) {
                ServingUnit.GRAMS
            },
            servingSize = servingSize,
            servingUnit = try {
                ServingUnit.valueOf(servingUnit)
            } catch (e: Exception) {
                ServingUnit.GRAMS
            },
            nutritionBasis = try {
                NutritionBasis.valueOf(nutritionBasis)
            } catch (e: Exception) {
                NutritionBasis.PER_100_G
            },
            energyKcal = energyKcal,
            proteinGrams = proteinGrams,
            carbohydratesGrams = carbohydratesGrams,
            sugarGrams = sugarGrams,
            addedSugarsGrams = addedSugarsGrams,
            fatGrams = fatGrams,
            saturatedFatGrams = saturatedFatGrams,
            transFatGrams = transFatGrams,
            fiberGrams = fiberGrams,
            sodiumMilligrams = sodiumMilligrams,
            saltGrams = saltGrams,
            fssaiLicenseNumber = fssaiLicenseNumber,
            source = try {
                ProductSource.valueOf(source)
            } catch (e: Exception) {
                ProductSource.LOCAL_USER
            },
            sourceProductId = sourceProductId,
            lastVerifiedAt = Instant.ofEpochMilli(lastVerifiedAt),
            isUserModified = isUserModified,
            completeness = try {
                DataCompleteness.valueOf(completeness)
            } catch (e: Exception) {
                DataCompleteness.PARTIAL
            },
            notes = notes
        )
    }

    companion object {
        fun fromDomain(product: FoodProduct): FoodProductEntity {
            val aliasesJson = try {
                JSONArray(product.brandAliases).toString()
            } catch (e: Exception) {
                "[]"
            }

            return FoodProductEntity(
                id = product.id,
                barcode = product.barcode,
                barcodeFormat = product.barcodeFormat.name,
                name = product.name,
                nameHi = product.nameHi,
                brand = product.brand,
                brandAliasesJson = aliasesJson,
                category = product.category,
                variant = product.variant,
                packageDescription = product.packageDescription,
                netQuantityValue = product.netQuantityValue,
                netQuantityUnit = product.netQuantityUnit.name,
                servingSize = product.servingSize,
                servingUnit = product.servingUnit.name,
                nutritionBasis = product.nutritionBasis.name,
                energyKcal = product.energyKcal,
                proteinGrams = product.proteinGrams,
                carbohydratesGrams = product.carbohydratesGrams,
                sugarGrams = product.sugarGrams,
                addedSugarsGrams = product.addedSugarsGrams,
                fatGrams = product.fatGrams,
                saturatedFatGrams = product.saturatedFatGrams,
                transFatGrams = product.transFatGrams,
                fiberGrams = product.fiberGrams,
                sodiumMilligrams = product.sodiumMilligrams,
                saltGrams = product.saltGrams,
                fssaiLicenseNumber = product.fssaiLicenseNumber,
                source = product.source.name,
                sourceProductId = product.sourceProductId,
                lastVerifiedAt = product.lastVerifiedAt.toEpochMilli(),
                isUserModified = product.isUserModified,
                completeness = product.completeness.name,
                notes = product.notes
            )
        }
    }
}

@Entity(
    tableName = "food_log_entries",
    indices = [
        Index(value = ["localDate"]),
        Index(value = ["loggedAt"]),
        Index(value = ["barcode"]),
        Index(value = ["externalRecordId"])
    ]
)
data class FoodLogEntryEntity(
    @PrimaryKey
    val id: String,
    val productId: String?,
    val barcode: String?,
    val productName: String,
    val brand: String?,
    val mealType: String,
    val loggedAt: Long,
    val localDate: String, // ISO-8601 YYYY-MM-DD
    val quantity: Double,
    val unit: String,
    val numberOfServings: Double?,
    val calculatedCalories: Double,
    val calculatedProtein: Double?,
    val calculatedCarbs: Double?,
    val calculatedFat: Double?,
    val calculatedSaturatedFat: Double? = null,
    val calculatedTransFat: Double? = null,
    val calculatedFiber: Double?,
    val calculatedSugar: Double?,
    val calculatedAddedSugars: Double? = null,
    val calculatedSodiumMg: Double?,
    val calculatedSaltG: Double? = null,
    val source: String,
    val externalRecordId: String?
) {
    fun toDomain(): FoodLogEntry {
        return FoodLogEntry(
            id = id,
            productId = productId,
            barcode = barcode,
            productName = productName,
            brand = brand,
            mealType = try {
                MealType.valueOf(mealType)
            } catch (e: Exception) {
                MealType.SNACK
            },
            loggedAt = Instant.ofEpochMilli(loggedAt),
            localDate = try {
                LocalDate.parse(localDate)
            } catch (e: Exception) {
                LocalDate.now()
            },
            quantity = quantity,
            unit = try {
                ServingUnit.valueOf(unit)
            } catch (e: Exception) {
                ServingUnit.GRAMS
            },
            numberOfServings = numberOfServings,
            calculatedCalories = calculatedCalories,
            calculatedProtein = calculatedProtein,
            calculatedCarbs = calculatedCarbs,
            calculatedFat = calculatedFat,
            calculatedSaturatedFat = calculatedSaturatedFat,
            calculatedTransFat = calculatedTransFat,
            calculatedFiber = calculatedFiber,
            calculatedSugar = calculatedSugar,
            calculatedAddedSugars = calculatedAddedSugars,
            calculatedSodiumMg = calculatedSodiumMg,
            calculatedSaltG = calculatedSaltG,
            source = try {
                ProductSource.valueOf(source)
            } catch (e: Exception) {
                ProductSource.LOCAL_USER
            },
            externalRecordId = externalRecordId
        )
    }

    companion object {
        fun fromDomain(entry: FoodLogEntry): FoodLogEntryEntity {
            return FoodLogEntryEntity(
                id = entry.id,
                productId = entry.productId,
                barcode = entry.barcode,
                productName = entry.productName,
                brand = entry.brand,
                mealType = entry.mealType.name,
                loggedAt = entry.loggedAt.toEpochMilli(),
                localDate = entry.localDate.toString(),
                quantity = entry.quantity,
                unit = entry.unit.name,
                numberOfServings = entry.numberOfServings,
                calculatedCalories = entry.calculatedCalories,
                calculatedProtein = entry.calculatedProtein,
                calculatedCarbs = entry.calculatedCarbs,
                calculatedFat = entry.calculatedFat,
                calculatedSaturatedFat = entry.calculatedSaturatedFat,
                calculatedTransFat = entry.calculatedTransFat,
                calculatedFiber = entry.calculatedFiber,
                calculatedSugar = entry.calculatedSugar,
                calculatedAddedSugars = entry.calculatedAddedSugars,
                calculatedSodiumMg = entry.calculatedSodiumMg,
                calculatedSaltG = entry.calculatedSaltG,
                source = entry.source.name,
                externalRecordId = entry.externalRecordId
            )
        }
    }
}
