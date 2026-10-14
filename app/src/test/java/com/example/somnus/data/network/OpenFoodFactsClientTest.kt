package com.example.somnus.data.network

import com.example.somnus.domain.model.DataCompleteness
import com.example.somnus.domain.model.ProductSource
import com.example.somnus.domain.model.ServingUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpenFoodFactsClientTest {

    private val client = OpenFoodFactsClient()

    @Test
    fun parseOpenFoodFactsJson_validProduct_parsesCorrectly() {
        val sampleJson = """
            {
                "status": 1,
                "code": "3017620422003",
                "product": {
                    "product_name": "Nutella",
                    "brands": "Ferrero",
                    "serving_size": "15 g",
                    "nutriments": {
                        "energy-kcal_100g": 539.0,
                        "proteins_100g": 6.3,
                        "carbohydrates_100g": 57.5,
                        "fat_100g": 30.9,
                        "sugars_100g": 56.3,
                        "sodium_100g": 0.042
                    }
                }
            }
        """.trimIndent()

        val product = client.parseOpenFoodFactsJson("3017620422003", sampleJson)

        assertNotNull(product)
        assertEquals("3017620422003", product!!.barcode)
        assertEquals("Nutella", product.name)
        assertEquals("Ferrero", product.brand)
        assertEquals(539.0, product.energyKcal, 0.1)
        assertEquals(6.3, product.proteinGrams!!, 0.01)
        assertEquals(57.5, product.carbohydratesGrams!!, 0.01)
        assertEquals(30.9, product.fatGrams!!, 0.01)
        assertEquals(56.3, product.sugarGrams!!, 0.01)
        assertEquals(42.0, product.sodiumMilligrams!!, 0.1) // 0.042g * 1000 = 42mg
        assertEquals(ProductSource.OPEN_FOOD_FACTS, product.source)
        assertEquals(DataCompleteness.COMPLETE, product.completeness)
    }

    @Test
    fun parseOpenFoodFactsJson_productNotFound_returnsNull() {
        val notFoundJson = """
            {
                "status": 0,
                "status_verbose": "product not found"
            }
        """.trimIndent()

        val product = client.parseOpenFoodFactsJson("0000000000000", notFoundJson)
        assertNull(product)
    }

    @Test
    fun parseOpenFoodFactsJson_missingNutrients_preservesNulls() {
        val partialJson = """
            {
                "status": 1,
                "product": {
                    "product_name": "Sparkling Water",
                    "nutriments": {
                        "energy-kcal_100g": 0.0
                    }
                }
            }
        """.trimIndent()

        val product = client.parseOpenFoodFactsJson("1111111111111", partialJson)

        assertNotNull(product)
        assertEquals("Sparkling Water", product!!.name)
        assertEquals(0.0, product.energyKcal, 0.1)
        assertNull(product.proteinGrams)
        assertNull(product.carbohydratesGrams)
        assertNull(product.fatGrams)
        assertNull(product.fiberGrams)
        assertEquals(DataCompleteness.MINIMAL, product.completeness)
    }

    @Test
    fun parseOpenFoodFactsJson_servingSizeString_extractsCorrectValueAndUnit() {
        val servingJson = """
            {
                "status": 1,
                "product": {
                    "product_name": "Almond Milk",
                    "serving_size": "240 ml",
                    "nutriments": {
                        "energy-kcal_100g": 30.0,
                        "proteins_100g": 1.0,
                        "carbohydrates_100g": 1.0,
                        "fat_100g": 2.5
                    }
                }
            }
        """.trimIndent()

        val product = client.parseOpenFoodFactsJson("2222222222222", servingJson)

        assertNotNull(product)
        assertEquals(240.0, product!!.servingSize!!, 0.1)
        assertEquals(ServingUnit.MILLILITERS, product.servingUnit)
    }
}
