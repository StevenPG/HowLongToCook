package com.stevenpg.howlongtocook.data.remote.s3

import com.stevenpg.howlongtocook.data.model.Library
import com.stevenpg.howlongtocook.data.remote.ConnectionInfo
import com.stevenpg.howlongtocook.data.remote.IncompatibleLibraryException
import com.stevenpg.howlongtocook.data.remote.LocalRecord
import com.stevenpg.howlongtocook.data.remote.RemoteStore
import com.stevenpg.howlongtocook.data.remote.RemoteSyncResult
import com.stevenpg.howlongtocook.data.sync.Merge
import com.stevenpg.howlongtocook.util.AppJson

/**
 * Stores the whole library as one JSON object (`<prefix>/library.json`) and uses
 * ETag-conditional writes for optimistic concurrency between devices.
 */
class S3RemoteStore(
    private val client: S3Client,
    private val key: String,
    override val description: String,
    private val clock: () -> Long = System::currentTimeMillis,
) : RemoteStore {

    override suspend fun testConnection(): ConnectionInfo {
        client.checkAccess(prefix = key.substringBeforeLast('/', ""))
        val existing = client.getObject(key) ?: return ConnectionInfo(productCount = null)
        return ConnectionInfo(productCount = decode(existing.bytes).products.count { !it.deleted })
    }

    override suspend fun sync(local: List<LocalRecord>, cursor: String?): RemoteSyncResult {
        val localProducts = local.map { it.product }
        var attempt = 0
        while (true) {
            attempt++
            val current = client.getObject(key)
            val remoteLib = current?.let { decode(it.bytes) }
            if (remoteLib != null && remoteLib.schemaVersion > Library.SCHEMA_VERSION) {
                throw IncompatibleLibraryException(remoteLib.schemaVersion)
            }
            val result = Merge.merge(localProducts, remoteLib?.products.orEmpty())
            if (result.remoteChanged) {
                val body = AppJson.encodeToString(
                    Library.serializer(),
                    Library(updatedAt = clock(), products = result.merged),
                ).encodeToByteArray()
                try {
                    client.putObject(key, body, ifMatch = current?.etag, ifNoneMatch = current == null)
                } catch (e: S3PreconditionFailed) {
                    if (attempt >= MAX_ATTEMPTS) throw e
                    continue // Someone else wrote in between: re-read and re-merge.
                } catch (e: S3ConditionalWritesUnsupported) {
                    client.putObject(key, body)
                }
            }
            val mergedById = result.merged.associateBy { it.id }
            val pushed = local.filter { it.dirty && mergedById[it.product.id] == it.product }
                .associate { it.product.id to it.product.updatedAt }
            return RemoteSyncResult(incoming = result.toLocal, pushed = pushed, cursor = null)
        }
    }

    private fun decode(bytes: ByteArray): Library =
        AppJson.decodeFromString(Library.serializer(), bytes.decodeToString())

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
