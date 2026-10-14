package com.example.somnus.data.repository

import com.example.somnus.data.local.FoodProductDao
import com.example.somnus.data.local.FoodProductEntity
import com.example.somnus.data.network.FoodDatabaseClient
import com.example.somnus.domain.model.FoodProduct
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FoodProductRepositoryImpl(
    private val foodProductDao: FoodProductDao,
    private val remoteClient: FoodDatabaseClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : FoodProductRepository {

    override fun findByBarcode(barcode: String): Flow<FoodProduct?> {
        return foodProductDao.findByBarcode(barcode).map { it?.toDomain() }
    }

    override suspend fun findByBarcodeSync(barcode: String): FoodProduct? = withContext(ioDispatcher) {
        foodProductDao.findByBarcodeSync(barcode)?.toDomain()
    }

    override fun searchByName(query: String): Flow<List<FoodProduct>> {
        return foodProductDao.searchByName(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getRecentProducts(limit: Int): Flow<List<FoodProduct>> {
        return foodProductDao.getRecentProducts(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveProduct(product: FoodProduct) = withContext(ioDispatcher) {
        foodProductDao.insert(FoodProductEntity.fromDomain(product))
    }

    override suspend fun deleteProduct(id: String) = withContext(ioDispatcher) {
        foodProductDao.deleteById(id)
    }

    override suspend fun lookupWithRemoteFallback(
        barcode: String,
        allowRemote: Boolean
    ): Result<FoodProduct?> = withContext(ioDispatcher) {
        // Step 1: Check local database index
        val localMatch = foodProductDao.findByBarcodeSync(barcode)
        if (localMatch != null) {
            return@withContext Result.success(localMatch.toDomain())
        }

        // Step 2: If offline or remote disallowed, return null
        if (!allowRemote) {
            return@withContext Result.success(null)
        }

        // Step 3: Remote lookup via Open Food Facts
        val remoteResult = remoteClient.lookupBarcode(barcode)
        if (remoteResult.isSuccess) {
            val product = remoteResult.getOrNull()
            if (product != null) {
                // Cache into local database for future offline access
                foodProductDao.insert(FoodProductEntity.fromDomain(product))
            }
            Result.success(product)
        } else {
            // Forward failure (e.g. network timeout or server error)
            Result.failure(remoteResult.exceptionOrNull() ?: RuntimeException("Lookup failed"))
        }
    }
}
