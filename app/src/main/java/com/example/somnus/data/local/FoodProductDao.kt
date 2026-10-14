package com.example.somnus.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodProductDao {

    @Query("SELECT * FROM food_products WHERE barcode = :barcode LIMIT 1")
    fun findByBarcode(barcode: String): Flow<FoodProductEntity?>

    @Query("SELECT * FROM food_products WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcodeSync(barcode: String): FoodProductEntity?

    @Query("SELECT * FROM food_products WHERE id = :id LIMIT 1")
    suspend fun findByIdSync(id: String): FoodProductEntity?

    @Query("SELECT * FROM food_products WHERE name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%' ORDER BY name ASC LIMIT 30")
    fun searchByName(query: String): Flow<List<FoodProductEntity>>

    @Query("SELECT * FROM food_products ORDER BY lastVerifiedAt DESC LIMIT :limit")
    fun getRecentProducts(limit: Int = 10): Flow<List<FoodProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: FoodProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<FoodProductEntity>)

    @Query("DELETE FROM food_products WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM food_products")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM food_products")
    suspend fun count(): Int
}
