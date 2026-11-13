package com.stevenpg.howlongtocook.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object LibraryRoute

@Serializable data class DetailRoute(val id: String)

@Serializable data class CookModeRoute(val id: String, val methodIndex: Int = 0)

/** [id] null = new product; [barcode] pre-fills a new product (from a scan). */
@Serializable data class EditorRoute(val id: String? = null, val barcode: String? = null)

/** [addToEditor] = return the scanned code to the editor instead of looking it up. */
@Serializable data class ScannerRoute(val addToEditor: Boolean = false)

@Serializable data object SettingsRoute

@Serializable data object StorageSettingsRoute

const val SCANNED_BARCODE_KEY = "scanned_barcode"
