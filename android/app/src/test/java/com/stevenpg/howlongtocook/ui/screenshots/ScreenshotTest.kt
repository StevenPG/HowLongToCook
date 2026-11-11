package com.stevenpg.howlongtocook.ui.screenshots

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.stevenpg.howlongtocook.data.local.AppDatabase
import com.stevenpg.howlongtocook.data.model.Product
import com.stevenpg.howlongtocook.data.model.TemperatureUnit
import com.stevenpg.howlongtocook.data.remote.openfoodfacts.OpenFoodFactsClient
import com.stevenpg.howlongtocook.data.repository.ProductRepository
import com.stevenpg.howlongtocook.data.settings.SettingsRepository
import com.stevenpg.howlongtocook.data.settings.ThemeMode
import com.stevenpg.howlongtocook.data.sync.SyncRequester
import com.stevenpg.howlongtocook.testing.sharedExample
import com.stevenpg.howlongtocook.ui.components.LocalTemperatureUnit
import com.stevenpg.howlongtocook.ui.detail.DetailContent
import com.stevenpg.howlongtocook.ui.detail.DetailUiState
import com.stevenpg.howlongtocook.ui.editor.EditorScreen
import com.stevenpg.howlongtocook.ui.editor.EditorViewModel
import com.stevenpg.howlongtocook.ui.library.LibraryContent
import com.stevenpg.howlongtocook.ui.library.LibraryFilter
import com.stevenpg.howlongtocook.ui.library.LibraryUiState
import com.stevenpg.howlongtocook.ui.library.SyncIndicator
import com.stevenpg.howlongtocook.ui.theme.HowLongToCookTheme
import com.stevenpg.howlongtocook.util.AppJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders key screens on the JVM. Run with `./gradlew testDebugUnitTest -Pscreenshots`
 * to write PNGs to app/build/screenshots for visual review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val tmp = TemporaryFolder()

    private val pie: Product = AppJson.decodeFromString(Product.serializer(), sharedExample("product.json"))
    private val others = listOf(
        pie.copy(id = "2", name = "Breakfast Burrito", brand = "Sunrise Kitchen", favorite = false, tags = listOf("breakfast")),
        pie.copy(id = "3", name = "Crispy Fish Fillets", brand = null, favorite = false, methods = pie.methods.reversed(), tags = listOf("dinner")),
        pie.copy(id = "4", name = "Mac & Cheese Bowl", brand = "Example Foods", favorite = true, methods = pie.methods.take(1)),
        pie.copy(id = "5", name = "Vegetable Potstickers", brand = "Golden Dragon", favorite = false, methods = pie.methods.takeLast(1)),
    )

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage("build/screenshots/$name${if (dark) "_dark" else ""}.png") {
            HowLongToCookTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalTemperatureUnit provides TemperatureUnit.F) {
                    Surface(color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
    }

    private val libraryState = LibraryUiState(
        loading = false,
        tags = listOf("breakfast", "dinner"),
        products = (listOf(pie) + others).sortedBy { it.name },
        recent = listOf(pie, others[0], others[2]),
        totalCount = 5,
        sync = SyncIndicator.SYNCED,
    )

    @Composable
    private fun Library(state: LibraryUiState) = LibraryContent(
        state = state, onQueryChange = {}, onFilterChange = {}, onToggleFavorite = {}, onRefresh = {},
        onOpenProduct = {}, onAddProduct = {}, onScan = {}, onOpenSettings = {},
    )

    @Test fun library() = shot("library") { Library(libraryState) }

    @Test fun libraryDark() = shot("library", dark = true) { Library(libraryState) }

    @Test fun libraryFiltered() = shot("library_filtered") {
        Library(libraryState.copy(filter = LibraryFilter.Favorites, products = libraryState.products.filter { it.favorite }))
    }

    @Test fun libraryEmpty() = shot("library_empty") {
        Library(LibraryUiState(loading = false, sync = SyncIndicator.LOCAL_ONLY))
    }

    @Composable
    private fun Detail(selected: Int) = DetailContent(
        state = DetailUiState(loading = false, product = pie, selectedMethod = selected),
        onBack = {}, onEdit = {}, onCookMode = { _, _ -> }, onSelectMethod = {}, onToggleFavorite = {}, onDelete = {},
    )

    @Test fun detail() = shot("detail") { Detail(0) }

    @Test fun detailDark() = shot("detail", dark = true) { Detail(0) }

    @Test fun detailOven() = shot("detail_oven") { Detail(1) }

    @Test fun editor() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = ProductRepository(db.productDao(), object : SyncRequester { override fun requestSync() = Unit })
        val settings = SettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                produceFile = { File(tmp.newFolder(), "s.preferences_pb") },
            ),
        )
        val off = OpenFoodFactsClient(HttpClient(MockEngine { respond("{}", HttpStatusCode.NotFound) }))
        val vm = EditorViewModel(SavedStateHandle(mapOf("barcode" to "012345678905")), repo, settings, off)
        vm.setName("Chicken Pot Pie")
        vm.setBrand("Example Foods")
        shot("editor") {
            EditorScreen(
                scannedBarcode = null, onScannedBarcodeConsumed = {}, onScanBarcode = {}, onClose = {},
                onSaved = { _, _ -> }, viewModel = vm,
            )
        }
        db.close()
    }
}
