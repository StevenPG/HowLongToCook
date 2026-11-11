package com.stevenpg.howlongtocook.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stevenpg.howlongtocook.data.model.CookingMethod
import com.stevenpg.howlongtocook.data.model.Product

@Entity(tableName = "products", indices = [Index("deleted"), Index("dirty")])
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String?,
    val barcodes: List<String>,
    val tags: List<String>,
    val favorite: Boolean,
    val rating: Int?,
    val notes: String?,
    val methods: List<CookingMethod>,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
    /** True when this version has not been pushed to the remote store yet. */
    val dirty: Boolean,
    /** Device-local; never synced. */
    val lastViewedAt: Long? = null,
) {
    fun toProduct() = Product(
        id = id,
        name = name,
        brand = brand,
        barcodes = barcodes,
        tags = tags,
        favorite = favorite,
        rating = rating,
        notes = notes,
        methods = methods,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deleted = deleted,
    )

    companion object {
        fun from(p: Product, dirty: Boolean, lastViewedAt: Long?) = ProductEntity(
            id = p.id,
            name = p.name,
            brand = p.brand,
            barcodes = p.barcodes,
            tags = p.tags,
            favorite = p.favorite,
            rating = p.rating,
            notes = p.notes,
            methods = p.methods,
            createdAt = p.createdAt,
            updatedAt = p.updatedAt,
            deleted = p.deleted,
            dirty = dirty,
            lastViewedAt = lastViewedAt,
        )
    }
}

/** Index for fast barcode lookups; a code may (rarely) belong to more than one product. */
@Entity(
    tableName = "barcodes",
    primaryKeys = ["code", "productId"],
    indices = [Index("productId")],
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BarcodeEntity(val code: String, val productId: String)
