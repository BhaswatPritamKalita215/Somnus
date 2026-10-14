package com.example.somnus.data.repository

import com.example.somnus.domain.model.FoodProduct
import kotlinx.coroutines.flow.Flow

interface FoodProductRepository {
    fun findByBarcode(barcode: String): Flow<FoodProduct?>
    suspend fun findByBarcodeSync(barcode: String): FoodProduct?
    fun searchByName(query: String): Flow<List<FoodProduct>>
    fun getRecentProducts(limit: Int = 10): Flow<List<FoodProduct>>
    suspend fun saveProduct(product: FoodProduct)
    suspend fun deleteProduct(id: String)
    suspend fun lookupWithRemoteFallback(barcode: String, allowRemote: Boolean = true): Result<FoodProduct?>
}
