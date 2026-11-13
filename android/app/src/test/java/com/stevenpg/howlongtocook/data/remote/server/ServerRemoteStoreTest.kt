package com.stevenpg.howlongtocook.data.remote.server

import com.stevenpg.howlongtocook.data.remote.ConnectionInfo
import com.stevenpg.howlongtocook.data.remote.LocalRecord
import com.stevenpg.howlongtocook.testing.FakeServer
import com.stevenpg.howlongtocook.testing.product
import com.stevenpg.howlongtocook.util.AppJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ServerRemoteStoreTest {
    private val token = "secret-token-123456"

    private fun store(server: FakeServer, tok: String = token) = ServerRemoteStore(
        HttpClient(server.engine) { install(ContentNegotiation) { json(AppJson) } },
        "http://10.0.2.2:8080/",
        tok,
    )

    @Test
    fun `pushes dirty products and advances the cursor`() = runTest {
        val server = FakeServer(token)
        val a = product("a", 1, "A")
        val result = store(server).sync(listOf(LocalRecord(a, true), LocalRecord(product("b", 1, "B"), false)), null)
        assertEquals(a, server.stored(a.id))
        assertEquals(null, server.stored(product("b", 1).id))
        assertEquals(mapOf(a.id to 1L), result.pushed)
        assertEquals("1", result.cursor)
        assertTrue(result.incoming.isEmpty())
    }

    @Test
    fun `server wins when its copy is newer`() = runTest {
        val server = FakeServer(token)
        server.put(product("a", 10, "Server"))
        val result = store(server).sync(listOf(LocalRecord(product("a", 5, "Local"), true)), "1")
        assertEquals(listOf("Server"), result.incoming.map { it.name })
        assertEquals("Server", server.stored(product("a", 1).id)!!.name)
    }

    @Test
    fun `pulls only changes since the cursor`() = runTest {
        val server = FakeServer(token)
        server.put(product("a", 1, "A"))
        server.put(product("b", 1, "B"))
        val result = store(server).sync(emptyList(), "1")
        assertEquals(listOf("B"), result.incoming.map { it.name })
        assertEquals("2", result.cursor)
    }

    @Test
    fun `bad token is reported`() = runTest {
        try {
            store(FakeServer(token), "wrong-token-000000").testConnection()
            fail("expected failure")
        } catch (e: ServerException) {
            assertEquals(401, e.status)
            assertEquals("Invalid server token", e.message)
        }
    }

    @Test
    fun `test connection describes the server`() = runTest {
        val server = FakeServer(token)
        server.put(product("a", 1))
        assertEquals(ConnectionInfo(productCount = 1, serverVersion = "0.1.0"), store(server).testConnection())
    }
}
