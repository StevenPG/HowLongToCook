package com.stevenpg.howlongtocook.testing

import com.stevenpg.howlongtocook.data.model.Product
import com.stevenpg.howlongtocook.data.remote.server.ChangesResponse
import com.stevenpg.howlongtocook.data.remote.server.ServerInfo
import com.stevenpg.howlongtocook.data.remote.server.WriteResult
import com.stevenpg.howlongtocook.util.AppJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf

/** Mirrors the Rust server's sync semantics (server/src/db.rs) in memory. */
class FakeServer(private val token: String = "secret-token-123456") {
    private data class Row(val product: Product, val revision: Long)

    private val rows = mutableMapOf<String, Row>()
    var revision = 0L
        private set

    fun stored(id: String): Product? = rows[id]?.product

    fun put(p: Product): Boolean {
        val existing = rows[p.id]
        if (existing != null && existing.product.updatedAt >= p.updatedAt) return false
        rows[p.id] = Row(p, ++revision)
        return true
    }

    private val json = headersOf("Content-Type", "application/json")

    val engine = MockEngine { req ->
        if (req.headers["Authorization"] != "Bearer $token") {
            return@MockEngine respond("""{"error":"unauthorized","message":"missing or invalid token"}""", HttpStatusCode.Unauthorized, json)
        }
        val path = req.url.encodedPath
        when {
            path == "/api/v1/info" -> respond(
                AppJson.encodeToString(ServerInfo.serializer(), ServerInfo("howlongtocook-server", "0.1.0", 1, rows.size.toLong(), revision)),
                HttpStatusCode.OK,
                json,
            )
            path == "/api/v1/products" && req.method == HttpMethod.Get -> {
                val since = req.url.parameters["since"]?.toLong() ?: 0
                val changed = rows.values.filter { it.revision > since }.sortedBy { it.revision }.map { it.product }
                respond(AppJson.encodeToString(ChangesResponse.serializer(), ChangesResponse(revision, changed)), HttpStatusCode.OK, json)
            }
            path.startsWith("/api/v1/products/") && req.method == HttpMethod.Put -> {
                val body = when (val b = req.body) {
                    is TextContent -> b.text
                    is OutgoingContent.ByteArrayContent -> b.bytes().decodeToString()
                    else -> error("unexpected body ${b::class}")
                }
                val p = AppJson.decodeFromString(Product.serializer(), body)
                val applied = put(p)
                respond(
                    AppJson.encodeToString(WriteResult.serializer(), WriteResult(rows.getValue(p.id).product, applied, revision)),
                    HttpStatusCode.OK,
                    json,
                )
            }
            else -> respond("""{"error":"not_found","message":"not found"}""", HttpStatusCode.NotFound, json)
        }
    }
}
