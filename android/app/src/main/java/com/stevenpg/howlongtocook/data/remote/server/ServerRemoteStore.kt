package com.stevenpg.howlongtocook.data.remote.server

import com.stevenpg.howlongtocook.data.model.Product
import com.stevenpg.howlongtocook.data.remote.ConnectionInfo
import com.stevenpg.howlongtocook.data.remote.LocalRecord
import com.stevenpg.howlongtocook.data.remote.RemoteStore
import com.stevenpg.howlongtocook.data.remote.RemoteSyncResult
import com.stevenpg.howlongtocook.util.AppJson
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable

@Serializable
data class ServerInfo(
    val name: String = "",
    val version: String = "",
    val schemaVersion: Int = 1,
    val productCount: Long = 0,
    val revision: Long = 0,
)

@Serializable
data class ChangesResponse(val revision: Long, val products: List<Product>)

@Serializable
data class WriteResult(val product: Product, val applied: Boolean, val revision: Long)

@Serializable
private data class ErrorBody(val error: String = "", val message: String = "")

class ServerException(val status: Int, message: String) : Exception(message)

/** Talks to the self-hosted Rust server (api/openapi.yaml). */
class ServerRemoteStore(
    private val http: HttpClient,
    baseUrl: String,
    private val token: String,
) : RemoteStore {
    private val base = baseUrl.trim().trimEnd('/')
    override val description: String = base

    override suspend fun testConnection(): ConnectionInfo {
        val res = http.get("$base/api/v1/info") { bearerAuth(token) }
        val info = AppJson.decodeFromString(ServerInfo.serializer(), res.ensureOk().bodyAsText())
        return ConnectionInfo(productCount = info.productCount.toInt(), serverVersion = info.version)
    }

    override suspend fun sync(local: List<LocalRecord>, cursor: String?): RemoteSyncResult {
        val incoming = linkedMapOf<String, Product>()
        val pushed = mutableMapOf<String, Long>()

        // 1. Push local changes; the server resolves conflicts with the same LWW rule.
        for (record in local.filter { it.dirty }) {
            val p = record.product
            val res = http.put("$base/api/v1/products/${p.id.encodeURLPathPart()}") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(AppJson.encodeToString(Product.serializer(), p))
            }
            val result = AppJson.decodeFromString(WriteResult.serializer(), res.ensureOk().bodyAsText())
            pushed[p.id] = p.updatedAt
            if (!result.applied && result.product != p) incoming[p.id] = result.product
        }

        // 2. Pull everything that changed since the last cursor.
        val since = cursor?.toLongOrNull() ?: 0L
        val res = http.get("$base/api/v1/products") {
            bearerAuth(token)
            parameter("since", since)
            parameter("includeDeleted", true)
        }
        val changes = AppJson.decodeFromString(ChangesResponse.serializer(), res.ensureOk().bodyAsText())
        val localById = local.associateBy { it.product.id }
        for (remote in changes.products) {
            val mine = localById[remote.id]?.product
            if (mine == null || remote.updatedAt >= mine.updatedAt) {
                if (mine != remote) incoming[remote.id] = remote
            }
        }
        return RemoteSyncResult(incoming.values.toList(), pushed, changes.revision.toString())
    }

    private suspend fun HttpResponse.ensureOk(): HttpResponse {
        if (status.value in 200..299) return this
        val body = runCatching { bodyAsText() }.getOrDefault("")
        val parsed = runCatching { AppJson.decodeFromString(ErrorBody.serializer(), body) }.getOrNull()
        val message = when (status.value) {
            401 -> "Invalid server token"
            404 -> "Server API not found — check the URL"
            else -> parsed?.message?.takeIf { it.isNotBlank() } ?: "Server error ${status.value}"
        }
        throw ServerException(status.value, message)
    }
}
