package com.stevenpg.howlongtocook.data.remote

import com.stevenpg.howlongtocook.data.model.Product

/** A local product as seen by sync. */
data class LocalRecord(val product: Product, val dirty: Boolean)

data class RemoteSyncResult(
    /** Newer remote versions to write locally (stored as clean). */
    val incoming: List<Product>,
    /** Local versions now persisted remotely: id -> updatedAt that was pushed. */
    val pushed: Map<String, Long>,
    /** New cursor to store, or null when the store does not use one. */
    val cursor: String?,
)

/** A remote destination for the library (S3/R2 bucket or the self-hosted server). */
interface RemoteStore {
    /** Human-readable label of the destination, for status messages. */
    val description: String

    /** Checks connectivity and credentials. Returns a short success message. */
    suspend fun testConnection(): String

    /** Reconciles [local] with the remote and returns what changed. */
    suspend fun sync(local: List<LocalRecord>, cursor: String?): RemoteSyncResult
}

class RemoteConfigurationException(message: String) : Exception(message)
