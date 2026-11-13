package com.stevenpg.howlongtocook.ui.text

import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.stevenpg.howlongtocook.data.model.ValidationIssue
import com.stevenpg.howlongtocook.data.remote.ConnectionInfo
import com.stevenpg.howlongtocook.data.remote.RemoteConfigurationException
import com.stevenpg.howlongtocook.data.remote.s3.S3Exception
import com.stevenpg.howlongtocook.data.remote.server.ServerException
import com.stevenpg.howlongtocook.data.repository.ValidationException
import com.stevenpg.howlongtocook.data.transfer.LibraryFileException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Every typed error and result must resolve to readable text from string resources. */
@RunWith(RobolectricTestRunner::class)
class MessagesTest {
    private val res: Resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
    private fun Throwable.text() = toUiText().resolve(res)

    @Test
    fun `remote errors`() {
        assertEquals(
            "Access denied — check the access key, secret and bucket permissions",
            S3Exception(403, "AccessDenied", "x").text(),
        )
        assertEquals("Bucket not found", S3Exception(404, "NoSuchBucket", "x").text())
        assertEquals("Storage error 500: InternalError", S3Exception(500, "InternalError", "x").text())
        assertEquals("Invalid server token", ServerException(401, "x").text())
        assertEquals("Enter a bucket name", RemoteConfigurationException(RemoteConfigurationException.Reason.MISSING_BUCKET).text())
        assertEquals("Can't reach nas.local — check the address and your connection", java.net.UnknownHostException("nas.local").text())
        assertEquals("Can't reach the server — check the address and your connection", java.net.UnknownHostException().text())
        assertEquals("Something odd", IllegalStateException("Something odd").text())
    }

    @Test
    fun `validation and file errors`() {
        val issue = ValidationIssue(ValidationIssue.Type.INVALID_BARCODE, value = "bad!")
        assertEquals("Invalid barcode “bad!”", issue.toUiText().resolve(res))
        assertEquals(
            "“Pot Pie”: Name must be at most 200 characters",
            ValidationException(listOf(ValidationIssue(ValidationIssue.Type.NAME_TOO_LONG, limit = 200)), "Pot Pie").text(),
        )
        assertEquals("Not a HowLongToCook export file", LibraryFileException(LibraryFileException.Reason.NOT_AN_EXPORT).text())
    }

    @Test
    fun `connection results use plurals`() {
        assertEquals("Connected. Library has 1 product.", ConnectionInfo(1).toUiText().resolve(res))
        assertEquals("Connected. Library has 3 products.", ConnectionInfo(3).toUiText().resolve(res))
        assertEquals("Connected. No library yet — it will be created on first sync.", ConnectionInfo(null).toUiText().resolve(res))
        assertEquals("Connected to server v0.1.0. 2 products.", ConnectionInfo(2, "0.1.0").toUiText().resolve(res))
    }

    @Test
    fun `nested arguments resolve`() {
        val text = uiText(com.stevenpg.howlongtocook.R.string.import_failed, uiText(com.stevenpg.howlongtocook.R.string.error_file_read))
        assertEquals("Import failed: Could not open the file", text.resolve(res))
    }
}
