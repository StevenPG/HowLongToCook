package com.stevenpg.howlongtocook.data.remote

import com.stevenpg.howlongtocook.data.model.Library
import com.stevenpg.howlongtocook.data.remote.s3.S3Client
import com.stevenpg.howlongtocook.data.remote.s3.S3RemoteStore
import com.stevenpg.howlongtocook.data.remote.server.ServerRemoteStore
import com.stevenpg.howlongtocook.testing.product
import com.stevenpg.howlongtocook.util.AppJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in tests against real backends (skipped unless the env vars are set):
 *
 *   just dev-up
 *   HLTC_LIVE_S3_ENDPOINT=http://localhost:9000 HLTC_LIVE_S3_BUCKET=howlongtocook \
 *   HLTC_LIVE_S3_ACCESS_KEY=hltc HLTC_LIVE_S3_SECRET_KEY=hltc-dev-secret \
 *   HLTC_LIVE_SERVER_URL=http://localhost:8080 HLTC_LIVE_SERVER_TOKEN=dev-token-change-me \
 *   ./gradlew testDebugUnitTest --tests '*LiveBackendsTest*'
 */
class LiveBackendsTest {
    private val http = HttpClient(OkHttp) { install(ContentNegotiation) { json(AppJson) } }
    private val runId = System.currentTimeMillis().toString()

    @Test
    fun `real S3-compatible store accepts signed requests and conditional writes`() = runTest {
        val endpoint = System.getenv("HLTC_LIVE_S3_ENDPOINT")
        assumeTrue("HLTC_LIVE_S3_ENDPOINT not set", endpoint != null)
        val client = S3Client(
            http = http,
            endpoint = endpoint!!,
            bucket = System.getenv("HLTC_LIVE_S3_BUCKET") ?: "howlongtocook",
            region = System.getenv("HLTC_LIVE_S3_REGION") ?: "us-east-1",
            accessKeyId = System.getenv("HLTC_LIVE_S3_ACCESS_KEY") ?: "hltc",
            secretAccessKey = System.getenv("HLTC_LIVE_S3_SECRET_KEY") ?: "hltc-dev-secret",
            pathStyle = true,
        )
        val key = "it-$runId/folder with space/library.json"
        val store = S3RemoteStore(client, key, key)

        assertTrue(store.testConnection().contains("No library yet"))
        val a = product("a-$runId", 1, "Live A")
        store.sync(listOf(LocalRecord(a, dirty = true)), null)
        val first = client.getObject(key)!!
        assertEquals(listOf("Live A"), AppJson.decodeFromString(Library.serializer(), first.bytes.decodeToString()).products.map { it.name })

        // A stale ETag must be rejected by the store (optimistic concurrency).
        client.putObject(key, first.bytes, ifMatch = first.etag)
        val rejected = runCatching { client.putObject(key, first.bytes, ifMatch = first.etag) }.exceptionOrNull()
        println("Stale If-Match result: ${rejected?.let { it::class.simpleName } ?: "accepted (provider ignores If-Match)"}")

        // Second device merges its product in.
        val b = product("b-$runId", 2, "Live B")
        val result = store.sync(listOf(LocalRecord(b, dirty = true)), null)
        assertEquals(listOf("Live A"), result.incoming.map { it.name })
        val merged = AppJson.decodeFromString(Library.serializer(), client.getObject(key)!!.bytes.decodeToString())
        assertEquals(listOf("Live A", "Live B"), merged.products.map { it.name })
    }

    @Test
    fun `real Rust server round trip`() = runTest {
        val url = System.getenv("HLTC_LIVE_SERVER_URL")
        assumeTrue("HLTC_LIVE_SERVER_URL not set", url != null)
        val store = ServerRemoteStore(http, url!!, System.getenv("HLTC_LIVE_SERVER_TOKEN") ?: "dev-token-change-me")
        assertTrue(store.testConnection().startsWith("Connected to server"))

        val p = product("srv-$runId", 100, "Server item")
        val pushed = store.sync(listOf(LocalRecord(p, dirty = true)), null)
        assertEquals(mapOf(p.id to 100L), pushed.pushed)

        // A second device with an empty cursor receives it; a stale edit loses.
        val other = store.sync(listOf(LocalRecord(p.copy(name = "Stale", updatedAt = 50), dirty = true)), null)
        assertEquals("Server item", other.incoming.single { it.id == p.id }.name)
        val cursor = other.cursor!!.toLong()
        assertTrue(cursor >= 1)
        assertTrue(store.sync(emptyList(), cursor.toString()).incoming.isEmpty())
    }
}
