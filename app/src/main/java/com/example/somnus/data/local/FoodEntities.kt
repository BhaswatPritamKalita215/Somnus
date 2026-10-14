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
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "food_products",
    indices = [
        Index(value = ["barcode"], unique = false),
        Index(value = ["name"])
    ]
)
data class FoodProductEntity(
    @PrimaryKey
    val id: String,
    val barcode: String,
    val barcodeFormat: String,
    val name: String,
    val brand: String?,
    val variant: String?,
    val packageDescription: String?,
    val servingSize: Double?,
    val servingUnit: String,
    val nutritionBasis: String,
    val energyKcal: Double,
    val proteinGrams: Double?,
    val carbohydratesGrams: Double?,
    val fatGrams: Double?,
    val fiberGrams: Double?,
    val sugarGrams: Double?,
    val sodiumMilligrams: Double?,
    val source: String,
    val sourceProductId: String?,
    val lastVerifiedAt: Long,
    val isUserModified: Boolean,
    val completeness: String,
    val notes: String?
) {
    fun toDomain(): FoodProduct {
        return FoodProduct(
            id = id,
            barcode = barcode,
            barcodeFormat = try {
                BarcodeFormat.valueOf(barcodeFormat)
            } catch (e: Exception) {
                BarcodeFormat.OTHER
            },
            name = name,
            brand = brand,
            variant = variant,
            packageDescription = packageDescription,
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
            fatGrams = fatGrams,
            fiberGrams = fiberGrams,
            sugarGrams = sugarGrams,
            sodiumMilligrams = sodiumMilligrams,
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
            return FoodProductEntity(
                id = product.id,
                barcode = product.barcode,
                barcodeFormat = product.barcodeFormat.name,
                name = product.name,
                brand = product.brand,
                variant = product.variant,
                packageDescription = product.packageDescription,
                servingSize = product.servingSize,
                servingUnit = product.servingUnit.name,
                nutritionBasis = product.nutritionBasis.name,
                energyKcal = product.energyKcal,
                proteinGrams = product.proteinGrams,
                carbohydratesGrams = product.carbohydratesGrams,
                fatGrams = product.fatGrams,
                fiberGrams = product.fiberGrams,
                sugarGrams = product.sugarGrams,
                sodiumMilligrams = product.sodiumMilligrams,
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
    val calculatedFiber: Double?,
    val calculatedSugar: Double?,
    val calculatedSodiumMg: Double?,
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
            calculatedFiber = calculatedFiber,
            calculatedSugar = calculatedSugar,
            calculatedSodiumMg = calculatedSodiumMg,
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
                calculatedFiber = entry.calculatedFiber,
                calculatedSugar = entry.calculatedSugar,
                calculatedSodiumMg = entry.calculatedSodiumMg,
                source = entry.source.name,
                externalRecordId = entry.externalRecordId
            )
        }
    }
}
