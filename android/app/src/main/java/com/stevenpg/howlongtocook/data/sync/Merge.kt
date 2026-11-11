package com.stevenpg.howlongtocook.data.sync

import com.stevenpg.howlongtocook.data.model.Product

/**
 * Last-write-wins merge shared by every backend (docs/DESIGN.md §6.3).
 * Ties go to the remote copy so all devices converge on the same version.
 */
object Merge {
    fun newer(local: Product?, remote: Product?): Product? = when {
        local == null -> remote
        remote == null -> local
        local.updatedAt > remote.updatedAt -> local
        else -> remote
    }

    data class Result(
        /** Full merged set (including tombstones), sorted by name for stable output. */
        val merged: List<Product>,
        /** Remote versions that must be written locally. */
        val toLocal: List<Product>,
        /** True when the remote copy differs from [merged] and must be rewritten. */
        val remoteChanged: Boolean,
    )

    fun merge(local: List<Product>, remote: List<Product>): Result {
        val localById = local.associateBy { it.id }
        val remoteById = remote.associateBy { it.id }
        val ids = localById.keys + remoteById.keys
        val merged = ids.mapNotNull { newer(localById[it], remoteById[it]) }
            .sortedWith(compareBy({ it.name.lowercase() }, { it.id }))
        val toLocal = merged.filter { m -> localById[m.id] != m }
        val remoteChanged = merged.size != remoteById.size || merged.any { remoteById[it.id] != it }
        return Result(merged, toLocal, remoteChanged)
    }
}
