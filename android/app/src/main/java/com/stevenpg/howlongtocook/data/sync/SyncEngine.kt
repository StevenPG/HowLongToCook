package com.stevenpg.howlongtocook.data.sync

import com.stevenpg.howlongtocook.data.local.ProductDao
import com.stevenpg.howlongtocook.data.local.ProductEntity
import com.stevenpg.howlongtocook.data.remote.LocalRecord
import com.stevenpg.howlongtocook.data.remote.RemoteStoreFactory
import com.stevenpg.howlongtocook.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SyncOutcome {
    data object NotConfigured : SyncOutcome
    data class Success(val pulled: Int, val pushed: Int) : SyncOutcome
    data class Failure(val message: String, val retryable: Boolean) : SyncOutcome
}

/** Runs one sync pass against whichever remote store is configured. */
@Singleton
class SyncEngine @Inject constructor(
    private val dao: ProductDao,
    private val settings: SettingsRepository,
    private val factory: RemoteStoreFactory,
) {
    private val mutex = Mutex()
    var clock: () -> Long = System::currentTimeMillis

    suspend fun sync(): SyncOutcome = mutex.withLock {
        val config = settings.current()
        val now = clock()
        val store = try {
            factory.create(config) ?: return SyncOutcome.NotConfigured
        } catch (e: Exception) {
            val msg = e.message ?: "Storage is not configured"
            settings.recordSyncFailure(now, msg)
            return SyncOutcome.Failure(msg, retryable = false)
        }
        settings.recordSyncAttempt(now)
        try {
            val local = dao.getAll().map { LocalRecord(it.toProduct(), it.dirty) }
            val result = store.sync(local, config.syncCursor)
            var applied = 0
            for (remote in result.incoming) {
                val current = dao.get(remote.id)
                // Never clobber an edit made while the sync was in flight.
                if (current != null && current.updatedAt > remote.updatedAt) continue
                dao.save(ProductEntity.from(remote, dirty = false, lastViewedAt = current?.lastViewedAt))
                applied++
            }
            result.pushed.forEach { (id, updatedAt) -> dao.markClean(id, updatedAt) }
            settings.recordSyncSuccess(clock(), result.cursor)
            return SyncOutcome.Success(pulled = applied, pushed = result.pushed.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = friendlyMessage(e)
            settings.recordSyncFailure(clock(), msg)
            return SyncOutcome.Failure(msg, retryable = isRetryable(e))
        }
    }

    private fun friendlyMessage(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "Can't reach ${e.message ?: "the server"} — check the address and your connection"
        is java.net.ConnectException -> "Connection refused — is the server running?"
        is java.net.SocketTimeoutException -> "The connection timed out"
        is javax.net.ssl.SSLException -> "Secure connection failed: ${e.message}"
        else -> e.message ?: e::class.simpleName ?: "Sync failed"
    }

    private fun isRetryable(e: Exception): Boolean = e is java.io.IOException
}
