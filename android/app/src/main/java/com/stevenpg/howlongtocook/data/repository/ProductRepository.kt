package com.stevenpg.howlongtocook.data.repository

import com.stevenpg.howlongtocook.data.local.ProductDao
import com.stevenpg.howlongtocook.data.local.ProductEntity
import com.stevenpg.howlongtocook.data.model.Library
import com.stevenpg.howlongtocook.data.model.Product
import com.stevenpg.howlongtocook.data.model.ProductValidation
import com.stevenpg.howlongtocook.data.model.ValidationIssue
import com.stevenpg.howlongtocook.data.sync.Merge
import com.stevenpg.howlongtocook.data.sync.SyncRequester
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Thrown when a product breaks the shared rules. [productName] is set for imports. */
class ValidationException(
    val issues: List<ValidationIssue>,
    val productName: String? = null,
) : Exception(issues.joinToString { it.type.name })

/** Thrown when an export file comes from a newer, incompatible app version. */
class NewerLibraryFileException : Exception("Export file was created by a newer app version")

data class ImportSummary(val received: Int, val applied: Int, val skipped: Int)

/** UI-facing access to products. All writes are local-first and trigger a background sync. */
@Singleton
class ProductRepository @Inject constructor(
    private val dao: ProductDao,
    private val sync: SyncRequester,
) {
    var clock: () -> Long = System::currentTimeMillis

    data class LibraryItem(val product: Product, val lastViewedAt: Long?)

    val items: Flow<List<LibraryItem>> =
        dao.observeActive().map { list -> list.map { LibraryItem(it.toProduct(), it.lastViewedAt) } }

    val pendingChanges: Flow<Int> = dao.observeDirtyCount()

    fun observe(id: String): Flow<Product?> = dao.observe(id).map { it?.takeUnless { e -> e.deleted }?.toProduct() }

    suspend fun get(id: String): Product? = dao.get(id)?.takeUnless { it.deleted }?.toProduct()

    suspend fun findByBarcode(code: String): List<Product> = dao.findByBarcode(code.trim()).map { it.toProduct() }

    fun newProduct(name: String = "", barcode: String? = null, brand: String? = null): Product {
        val now = clock()
        return Product(
            id = UUID.randomUUID().toString(),
            name = name,
            brand = brand,
            barcodes = listOfNotNull(barcode?.trim()?.takeIf { it.isNotEmpty() }),
            createdAt = now,
            updatedAt = now,
        )
    }

    /** Validates, stamps and stores a product. Throws [ValidationException]. */
    suspend fun save(product: Product): Product {
        val (normalized, errors) = ProductValidation.normalize(product)
        if (errors.isNotEmpty()) throw ValidationException(errors)
        val existing = dao.get(product.id)
        val stamped = normalized.copy(
            updatedAt = maxOf(clock(), (existing?.updatedAt ?: 0) + 1),
            deleted = false,
        )
        dao.save(ProductEntity.from(stamped, dirty = true, lastViewedAt = existing?.lastViewedAt))
        sync.requestSync()
        return stamped
    }

    /** Tombstones a product and returns the previous version for undo. */
    suspend fun delete(id: String): Product? {
        val existing = dao.get(id) ?: return null
        val previous = existing.toProduct()
        val tombstone = previous.copy(deleted = true, updatedAt = maxOf(clock(), existing.updatedAt + 1))
        dao.save(ProductEntity.from(tombstone, dirty = true, lastViewedAt = existing.lastViewedAt))
        sync.requestSync()
        return previous
    }

    suspend fun restore(previous: Product) {
        save(previous.copy(deleted = false))
    }

    suspend fun setFavorite(id: String, favorite: Boolean) {
        val p = get(id) ?: return
        if (p.favorite != favorite) save(p.copy(favorite = favorite))
    }

    suspend fun markViewed(id: String) = dao.setLastViewed(id, clock())

    suspend fun exportLibrary(): Library =
        Library(updatedAt = clock(), products = dao.getAll().map { it.toProduct() }.sortedBy { it.name.lowercase() })

    /** Merges an export file with last-write-wins; applied products are queued for upload. */
    suspend fun importLibrary(library: Library): ImportSummary {
        if (library.schemaVersion > Library.SCHEMA_VERSION) throw NewerLibraryFileException()
        val incoming = library.products.map { p ->
            val (normalized, issues) = ProductValidation.normalize(p)
            if (issues.isNotEmpty()) throw ValidationException(issues, productName = p.name)
            normalized
        }
        var applied = 0
        for (p in incoming) {
            val existing = dao.get(p.id)
            if (Merge.newer(existing?.toProduct(), p) === p && existing?.toProduct() != p) {
                dao.save(ProductEntity.from(p, dirty = true, lastViewedAt = existing?.lastViewedAt))
                applied++
            }
        }
        if (applied > 0) sync.requestSync()
        return ImportSummary(incoming.size, applied, incoming.size - applied)
    }

    /** Called when the remote destination changes so everything is uploaded there. */
    suspend fun markAllForUpload() = dao.markAllDirty()
}
