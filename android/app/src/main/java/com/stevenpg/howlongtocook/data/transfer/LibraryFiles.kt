package com.stevenpg.howlongtocook.data.transfer

import android.content.Context
import android.net.Uri
import com.stevenpg.howlongtocook.data.model.Library
import com.stevenpg.howlongtocook.data.repository.ImportSummary
import com.stevenpg.howlongtocook.data.repository.ProductRepository
import com.stevenpg.howlongtocook.util.AppJson
import com.stevenpg.howlongtocook.util.PrettyJson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads/writes library backups through the Storage Access Framework. */
@Singleton
class LibraryFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ProductRepository,
) {
    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val library = repository.exportLibrary()
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(PrettyJson.encodeToString(Library.serializer(), library).encodeToByteArray())
        } ?: error("Could not open the file for writing")
        library.products.count { !it.deleted }
    }

    suspend fun import(uri: Uri): ImportSummary {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: error("Could not open the file")
        }
        val library = try {
            AppJson.decodeFromString(Library.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a HowLongToCook export file")
        }
        return repository.importLibrary(library)
    }

    fun suggestedFileName(): String =
        "howlongtocook-${java.time.LocalDate.now()}.json"
}
