package com.stevenpg.howlongtocook.ui.editor

import android.content.Context
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stevenpg.howlongtocook.data.local.AppDatabase
import com.stevenpg.howlongtocook.data.remote.openfoodfacts.OpenFoodFactsClient
import com.stevenpg.howlongtocook.data.repository.ProductRepository
import com.stevenpg.howlongtocook.data.settings.SettingsRepository
import com.stevenpg.howlongtocook.data.sync.SyncRequester
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class EditorViewModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    @After
    fun close() = db.close()

    /** Settings that stay unreadable until [release] completes, so the editor's load is held open. */
    private class GatedPreferences(private val release: CompletableDeferred<Unit>) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow {
            release.await()
            emit(emptyPreferences())
        }
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(emptyPreferences())
    }

    private fun viewModel(
        vararg args: Pair<String, Any?>,
        settingsReady: CompletableDeferred<Unit> = CompletableDeferred(Unit),
    ) = EditorViewModel(
        SavedStateHandle(mapOf(*args)),
        ProductRepository(db.productDao(), object : SyncRequester { override fun requestSync() = Unit }),
        SettingsRepository(GatedPreferences(settingsReady)),
        OpenFoodFactsClient(HttpClient(MockEngine { respond("{}", HttpStatusCode.NotFound) })),
    )

    /** Runs main-thread work until the ViewModel has finished loading (settings load on IO). */
    private fun EditorViewModel.awaitLoaded() {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            if (!state.value.loading) return
            Thread.sleep(10)
        }
        error("editor never finished loading")
    }

    @Test
    fun `edits made before loading finishes are not lost`() {
        val settingsReady = CompletableDeferred<Unit>()
        val vm = viewModel("barcode" to "012345678905", settingsReady = settingsReady)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("editor must still be loading", vm.state.value.loading)

        // e.g. a barcode handed back by the scanner, or fast typing, while still loading.
        vm.setName("Pot Pie")
        vm.addBarcode("9876543210")
        settingsReady.complete(Unit)
        vm.awaitLoaded()
        shadowOf(Looper.getMainLooper()).idle()

        val s = vm.state.value
        assertEquals("Pot Pie", s.name)
        assertEquals(listOf("012345678905", "9876543210"), s.barcodes)
        assertTrue(s.changed)
    }

    @Test
    fun `new product starts unchanged with a default microwave method`() {
        val vm = viewModel()
        vm.awaitLoaded()
        val s = vm.state.value
        assertFalse(s.changed)
        assertTrue(s.isNew)
        assertEquals(1, s.methods.size)
    }
}
