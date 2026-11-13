package com.stevenpg.howlongtocook.data.remote.s3

import com.stevenpg.howlongtocook.data.model.Library
import com.stevenpg.howlongtocook.data.remote.ConnectionInfo
import com.stevenpg.howlongtocook.data.remote.LocalRecord
import com.stevenpg.howlongtocook.testing.FakeS3
import com.stevenpg.howlongtocook.testing.product
import com.stevenpg.howlongtocook.util.AppJson
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class S3RemoteStoreTest {
    private val path = "/bucket/hltc/library.json"

    private fun store(s3: FakeS3, pathStyle: Boolean = true) = S3RemoteStore(
        S3Client(HttpClient(s3.engine), "http://10.0.2.2:9000", "bucket", "us-east-1", "AK", "SK", pathStyle),
        "hltc/library.json",
        "bucket/hltc/library.json",
        clock = { 42 },
    )

    private fun remoteNames(s3: FakeS3) =
        AppJson.decodeFromString(Library.serializer(), s3.read(path)!!).products.map { it.name }

    @Test
    fun `first sync creates the library`() = runTest {
        val s3 = FakeS3()
        val a = product("a", 1, "A")
        val result = store(s3).sync(listOf(LocalRecord(a, dirty = true)), null)
        assertEquals(listOf("A"), remoteNames(s3))
        assertEquals(mapOf(a.id to 1L), result.pushed)
        assertTrue(result.incoming.isEmpty())
    }

    @Test
    fun `pulls remote changes without rewriting when nothing local changed`() = runTest {
        val s3 = FakeS3()
        s3.write(path, AppJson.encodeToString(Library.serializer(), Library(products = listOf(product("b", 5, "B")))))
        val result = store(s3).sync(emptyList(), null)
        assertEquals(listOf("B"), result.incoming.map { it.name })
        assertEquals(0, s3.putCount)
    }

    @Test
    fun `retries and re-merges after a concurrent write`() = runTest {
        val s3 = FakeS3()
        s3.write(path, AppJson.encodeToString(Library.serializer(), Library(products = listOf(product("x", 1, "X")))))
        s3.beforeNextPut = {
            s3.write(path, AppJson.encodeToString(Library.serializer(), Library(products = listOf(product("x", 1, "X"), product("other", 3, "Other device")))))
        }
        val mine = product("a", 2, "Mine")
        val result = store(s3).sync(listOf(LocalRecord(mine, dirty = true)), null)
        assertEquals(1, s3.conflicts)
        assertEquals(listOf("Mine", "Other device", "X"), remoteNames(s3))
        assertEquals(setOf("Other device", "X"), result.incoming.map { it.name }.toSet())
    }

    @Test
    fun `falls back when conditional writes are unsupported`() = runTest {
        val s3 = FakeS3(supportsConditionalWrites = false)
        store(s3).sync(listOf(LocalRecord(product("a", 1, "A"), dirty = true)), null)
        assertEquals(listOf("A"), remoteNames(s3))
    }

    @Test
    fun `virtual hosted addressing puts the bucket in the host`() = runTest {
        val s3 = FakeS3()
        store(s3, pathStyle = false).sync(listOf(LocalRecord(product("a", 1, "A"), dirty = true)), null)
        assertEquals(setOf("/hltc/library.json"), s3.objects.keys)
    }

    @Test
    fun `access denied surfaces a friendly message`() = runTest {
        val s3 = FakeS3().apply { denyAll = true }
        try {
            store(s3).testConnection()
            fail("expected failure")
        } catch (e: S3Exception) {
            assertEquals(403, e.status)
            assertTrue(e.message!!.startsWith("Access denied"))
        }
    }

    @Test
    fun `test connection reports library size`() = runTest {
        val s3 = FakeS3()
        assertEquals(ConnectionInfo(productCount = null), store(s3).testConnection())
        store(s3).sync(listOf(LocalRecord(product("a", 1, "A"), dirty = true)), null)
        assertEquals(ConnectionInfo(productCount = 1), store(s3).testConnection())
    }
}
