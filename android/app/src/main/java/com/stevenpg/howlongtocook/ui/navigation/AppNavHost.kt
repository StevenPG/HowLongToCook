package com.stevenpg.howlongtocook.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.stevenpg.howlongtocook.ui.detail.CookModeScreen
import com.stevenpg.howlongtocook.ui.detail.DetailScreen
import com.stevenpg.howlongtocook.ui.editor.EditorScreen
import com.stevenpg.howlongtocook.ui.library.LibraryScreen
import com.stevenpg.howlongtocook.ui.scanner.ScannerScreen
import com.stevenpg.howlongtocook.ui.settings.SettingsScreen
import com.stevenpg.howlongtocook.ui.settings.StorageSettingsScreen

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = LibraryRoute,
        enterTransition = { slideInHorizontally { it / 6 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 6 } + fadeOut() },
    ) {
        composable<LibraryRoute> {
            LibraryScreen(
                onOpenProduct = { nav.navigate(DetailRoute(it)) },
                onAddProduct = { nav.navigate(EditorRoute()) },
                onScan = { nav.navigate(ScannerRoute()) },
                onOpenSettings = { nav.navigate(SettingsRoute) },
            )
        }
        composable<DetailRoute> {
            DetailScreen(
                onBack = { nav.popBackStack() },
                onEdit = { id -> nav.navigate(EditorRoute(id = id)) },
                onCookMode = { id, method -> nav.navigate(CookModeRoute(id, method)) },
            )
        }
        composable<CookModeRoute> { entry ->
            CookModeScreen(
                methodIndex = entry.toRoute<CookModeRoute>().methodIndex,
                onClose = { nav.popBackStack() },
            )
        }
        composable<EditorRoute> { entry ->
            val scanned by entry.savedStateHandle.getStateFlow<String?>(SCANNED_BARCODE_KEY, null)
                .collectAsStateWithLifecycle()
            EditorScreen(
                scannedBarcode = scanned,
                onScannedBarcodeConsumed = { entry.savedStateHandle[SCANNED_BARCODE_KEY] = null },
                onScanBarcode = { nav.navigate(ScannerRoute(addToEditor = true)) },
                onClose = { nav.popBackStack() },
                onSaved = { id, isNew ->
                    if (isNew) {
                        nav.navigate(DetailRoute(id)) { popUpTo<EditorRoute> { inclusive = true } }
                    } else {
                        nav.popBackStack()
                    }
                },
            )
        }
        composable<ScannerRoute> {
            ScannerScreen(
                onBack = { nav.popBackStack() },
                onReturnBarcode = { code ->
                    nav.previousBackStackEntry?.savedStateHandle?.set(SCANNED_BARCODE_KEY, code)
                    nav.popBackStack()
                },
                onOpenProduct = { id -> nav.navigate(DetailRoute(id)) { popUpTo<ScannerRoute> { inclusive = true } } },
                onCreateProduct = { code -> nav.navigate(EditorRoute(barcode = code)) { popUpTo<ScannerRoute> { inclusive = true } } },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenStorage = { nav.navigate(StorageSettingsRoute) },
            )
        }
        composable<StorageSettingsRoute> {
            StorageSettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
