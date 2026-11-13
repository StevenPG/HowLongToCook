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

    /** Checks connectivity and credentials. */
    suspend fun testConnection(): ConnectionInfo

    /** Reconciles [local] with the remote and returns what changed. */
    suspend fun sync(local: List<LocalRecord>, cursor: String?): RemoteSyncResult
}

/**
 * Result of a successful connection test. [productCount] is null when the destination
 * has no library yet; [serverVersion] is set for the self-hosted server.
 */
data class ConnectionInfo(val productCount: Int?, val serverVersion: String? = null)

/** The remote settings are incomplete or invalid; [reason] maps to a localized message. */
class RemoteConfigurationException(val reason: Reason) : Exception(reason.name) {
    enum class Reason {
        MISSING_ACCOUNT_ID,
        MISSING_ENDPOINT,
        INVALID_ENDPOINT,
        MISSING_BUCKET,
        MISSING_ACCESS_KEY,
        MISSING_SECRET,
        INVALID_SERVER_URL,
        MISSING_TOKEN,
    }
}

/** The remote library was written by a newer, incompatible app version. */
class IncompatibleLibraryException(val schemaVersion: Int) :
    Exception("Remote library schemaVersion $schemaVersion is newer than supported")
