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

class LibraryFileException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { CANNOT_READ, CANNOT_WRITE, NOT_AN_EXPORT }
}

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
        } ?: throw LibraryFileException(LibraryFileException.Reason.CANNOT_WRITE)
        library.products.count { !it.deleted }
    }

    suspend fun import(uri: Uri): ImportSummary {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: throw LibraryFileException(LibraryFileException.Reason.CANNOT_READ)
        }
        val library = try {
            AppJson.decodeFromString(Library.serializer(), text)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw LibraryFileException(LibraryFileException.Reason.NOT_AN_EXPORT)
        } catch (e: IllegalArgumentException) {
            throw LibraryFileException(LibraryFileException.Reason.NOT_AN_EXPORT)
        }
        return repository.importLibrary(library)
    }

    fun suggestedFileName(): String =
        "howlongtocook-${java.time.LocalDate.now()}.json"
}
