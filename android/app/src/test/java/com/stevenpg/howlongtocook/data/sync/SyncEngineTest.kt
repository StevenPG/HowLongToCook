package com.stevenpg.howlongtocook.data.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stevenpg.howlongtocook.data.local.AppDatabase
import com.stevenpg.howlongtocook.data.model.CookingMethod
import com.stevenpg.howlongtocook.data.model.MethodType
import com.stevenpg.howlongtocook.data.remote.RemoteStoreFactory
import com.stevenpg.howlongtocook.data.repository.ProductRepository
import com.stevenpg.howlongtocook.data.settings.S3Provider
import com.stevenpg.howlongtocook.data.settings.S3Settings
import com.stevenpg.howlongtocook.data.settings.SecretKind
import com.stevenpg.howlongtocook.data.settings.SecretStore
import com.stevenpg.howlongtocook.data.settings.ServerSettings
import com.stevenpg.howlongtocook.data.settings.SettingsRepository
import com.stevenpg.howlongtocook.data.settings.StorageMode
import com.stevenpg.howlongtocook.testing.FakeS3
import com.stevenpg.howlongtocook.testing.FakeServer
import com.stevenpg.howlongtocook.util.AppJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * End-to-end sync between two simulated devices (separate Room databases and settings)
 * sharing one fake bucket or fake server.
 */
@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val devices = mutableListOf<Device>()
    private var clock = 1_000L

    private class FakeSecrets : SecretStore {
        val map = mutableMapOf<SecretKind, String>()
        override suspend fun get(kind: SecretKind) = map[kind]
        override suspend fun put(kind: SecretKind, value: String?) {
            if (value == null) map.remove(kind) else map[kind] = value
        }
    }

    private inner class Device(engine: MockEngine) {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                produceFile = { File(tmp.newFolder(), "settings.preferences_pb") },
            ),
        )
        val secrets = FakeSecrets()
        var syncRequests = 0
        val repo = ProductRepository(db.productDao(), object : SyncRequester {
            override fun requestSync() { syncRequests++ }
        }).also { it.clock = { ++clock } }
        val engine = SyncEngine(
            db.productDao(),
            settings,
            RemoteStoreFactory(HttpClient(engine) { install(ContentNegotiation) { json(AppJson) } }, secrets),
        ).also { it.clock = { ++clock } }

        suspend fun names() = repo.items.first().map { it.product.name }.sorted()
    }

    private suspend fun s3Device(s3: FakeS3) = Device(s3.engine).also {
        it.secrets.put(SecretKind.S3_SECRET_ACCESS_KEY, "secret")
        it.settings.saveRemote(
            StorageMode.S3,
            S3Settings(provider = S3Provider.CUSTOM, endpoint = "http://minio:9000", bucket = "b", accessKeyId = "ak"),
            ServerSettings(),
        )
        devices += it
    }

    private suspend fun serverDevice(server: FakeServer) = Device(server.engine).also {
        it.secrets.put(SecretKind.SERVER_TOKEN, "secret-token-123456")
        it.settings.saveRemote(StorageMode.SERVER, S3Settings(), ServerSettings("http://server:8080"))
        devices += it
    }

    @After
    fun tearDown() = devices.forEach { it.db.close() }

    @Test
    fun `two devices converge through a bucket`() = runTest {
        val s3 = FakeS3()
        val phone = s3Device(s3)
        val tablet = s3Device(s3)

        val pie = phone.repo.save(phone.repo.newProduct("Pot Pie", barcode = "012345678905"))
        tablet.repo.save(tablet.repo.newProduct("Lasagna"))
        assertEquals(1, phone.syncRequests)

        assertTrue(phone.engine.sync() is SyncOutcome.Success)
        assertTrue(tablet.engine.sync() is SyncOutcome.Success)
        phone.engine.sync()
        assertEquals(listOf("Lasagna", "Pot Pie"), phone.names())
        assertEquals(listOf("Lasagna", "Pot Pie"), tablet.names())
        assertEquals(0, phone.repo.pendingChanges.first())

        // Barcode lookups work for synced products too.
        assertEquals("Pot Pie", tablet.repo.findByBarcode("012345678905").single().name)

        // Tablet edits, phone deletes something else; both propagate.
        tablet.repo.save(tablet.repo.get(pie.id)!!.copy(methods = listOf(CookingMethod(MethodType.OVEN))))
        val lasagna = phone.repo.items.first().first { it.product.name == "Lasagna" }.product
        phone.repo.delete(lasagna.id)
        tablet.engine.sync(); phone.engine.sync(); tablet.engine.sync()

        assertEquals(listOf("Pot Pie"), phone.names())
        assertEquals(listOf("Pot Pie"), tablet.names())
        assertEquals(MethodType.OVEN, phone.repo.get(pie.id)!!.methods.single().type)
        assertNull(tablet.repo.get(lasagna.id))
    }

    @Test
    fun `two devices converge through the server`() = runTest {
        val server = FakeServer()
        val phone = serverDevice(server)
        val tablet = serverDevice(server)
        phone.repo.save(phone.repo.newProduct("Burrito"))
        phone.engine.sync()
        tablet.engine.sync()
        assertEquals(listOf("Burrito"), tablet.names())
        assertEquals("1", tablet.settings.current().syncCursor)
    }

    @Test
    fun `last write wins on conflicting edits`() = runTest {
        val s3 = FakeS3()
        val phone = s3Device(s3)
        val tablet = s3Device(s3)
        val p = phone.repo.save(phone.repo.newProduct("Soup"))
        phone.engine.sync(); tablet.engine.sync()

        phone.repo.save(p.copy(name = "Soup (phone)"))
        tablet.repo.save(p.copy(name = "Soup (tablet)")) // later clock tick
        phone.engine.sync(); tablet.engine.sync(); phone.engine.sync()

        assertEquals(listOf("Soup (tablet)"), phone.names())
        assertEquals(listOf("Soup (tablet)"), tablet.names())
    }

    @Test
    fun `switching destination re-uploads everything`() = runTest {
        val s3 = FakeS3()
        val phone = s3Device(s3)
        phone.repo.save(phone.repo.newProduct("Pizza"))
        phone.engine.sync()
        assertEquals(0, phone.repo.pendingChanges.first())

        val changed = phone.settings.saveRemote(
            StorageMode.S3,
            S3Settings(provider = S3Provider.CUSTOM, endpoint = "http://minio:9000", bucket = "b", accessKeyId = "ak", prefix = "other"),
            ServerSettings(),
        )
        assertTrue(changed)
        phone.repo.markAllForUpload()
        phone.engine.sync()
        assertTrue(s3.read("/b/other/library.json")!!.contains("Pizza"))
    }

    @Test
    fun `failures are recorded with a readable message`() = runTest {
        val s3 = FakeS3().apply { denyAll = true }
        val phone = s3Device(s3)
        phone.repo.save(phone.repo.newProduct("Waffles"))
        val outcome = phone.engine.sync()
        assertTrue(outcome is SyncOutcome.Failure)
        assertTrue(phone.settings.current().sync.lastError!!.startsWith("Access denied"))
        assertEquals(1, phone.repo.pendingChanges.first())
    }

    @Test
    fun `local only mode does nothing`() = runTest {
        val device = Device(FakeS3().engine)
        devices += device
        assertEquals(SyncOutcome.NotConfigured, device.engine.sync())
    }

    @Test
    fun `import merges with last write wins`() = runTest {
        val phone = Device(FakeS3().engine).also { devices += it }
        val p = phone.repo.save(phone.repo.newProduct("Tacos"))
        val export = phone.repo.exportLibrary()
        val summary = phone.repo.importLibrary(
            export.copy(products = export.products + export.products.first().copy(id = "00000000-0000-4000-8000-000000000001", name = "Nachos")),
        )
        assertEquals(2, summary.received)
        assertEquals(1, summary.applied)
        assertEquals(listOf("Nachos", "Tacos"), phone.names())
        assertEquals(p.updatedAt, phone.repo.get(p.id)!!.updatedAt)
    }
}
