package com.stevenpg.howlongtocook.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    fun observe(id: String): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun get(id: String): ProductEntity?

    @Query("SELECT * FROM products")
    suspend fun getAll(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE dirty = 1")
    suspend fun getDirty(): List<ProductEntity>

    @Query(
        "SELECT p.* FROM products p JOIN barcodes b ON b.productId = p.id " +
            "WHERE b.code = :code AND p.deleted = 0 ORDER BY p.name COLLATE NOCASE",
    )
    suspend fun findByBarcode(code: String): List<ProductEntity>

    @Query("SELECT COUNT(*) FROM products WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Upsert
    suspend fun upsertEntity(entity: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBarcodes(barcodes: List<BarcodeEntity>)

    @Query("DELETE FROM barcodes WHERE productId = :productId")
    suspend fun deleteBarcodes(productId: String)

    @Query("UPDATE products SET lastViewedAt = :at WHERE id = :id")
    suspend fun setLastViewed(id: String, at: Long)

    /** Clears the dirty flag only if the row was not edited again since it was pushed. */
    @Query("UPDATE products SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long)

    @Query("UPDATE products SET dirty = 1")
    suspend fun markAllDirty()

    /** Writes a product and keeps the barcode index in sync. */
    @Transaction
    suspend fun save(entity: ProductEntity) {
        upsertEntity(entity)
        deleteBarcodes(entity.id)
        if (!entity.deleted && entity.barcodes.isNotEmpty()) {
            insertBarcodes(entity.barcodes.map { BarcodeEntity(it, entity.id) })
        }
    }
}
