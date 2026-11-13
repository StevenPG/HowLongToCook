package com.stevenpg.howlongtocook.ui.text

import com.stevenpg.howlongtocook.R
import com.stevenpg.howlongtocook.data.model.ValidationIssue
import com.stevenpg.howlongtocook.data.remote.ConnectionInfo
import com.stevenpg.howlongtocook.data.remote.IncompatibleLibraryException
import com.stevenpg.howlongtocook.data.remote.RemoteConfigurationException
import com.stevenpg.howlongtocook.data.remote.RemoteConfigurationException.Reason
import com.stevenpg.howlongtocook.data.remote.s3.S3ConditionalWritesUnsupported
import com.stevenpg.howlongtocook.data.remote.s3.S3Exception
import com.stevenpg.howlongtocook.data.remote.s3.S3PreconditionFailed
import com.stevenpg.howlongtocook.data.remote.server.ServerException
import com.stevenpg.howlongtocook.data.repository.NewerLibraryFileException
import com.stevenpg.howlongtocook.data.repository.ValidationException
import com.stevenpg.howlongtocook.data.transfer.LibraryFileException

/** Localized text for a validation rule violation. */
fun ValidationIssue.toUiText(): UiText = when (type) {
    ValidationIssue.Type.NAME_REQUIRED -> uiText(R.string.validation_name_required)
    ValidationIssue.Type.NAME_TOO_LONG -> uiPlural(R.plurals.validation_name_too_long, limit ?: 0, limit ?: 0)
    ValidationIssue.Type.INVALID_BARCODE -> uiText(R.string.validation_invalid_barcode, value.orEmpty())
    ValidationIssue.Type.TOO_MANY_BARCODES -> uiPlural(R.plurals.validation_too_many_barcodes, limit ?: 0, limit ?: 0)
    ValidationIssue.Type.TAG_TOO_LONG -> uiPlural(R.plurals.validation_tag_too_long, limit ?: 0, limit ?: 0)
    ValidationIssue.Type.TOO_MANY_TAGS -> uiPlural(R.plurals.validation_too_many_tags, limit ?: 0, limit ?: 0)
    ValidationIssue.Type.RATING_OUT_OF_RANGE -> uiText(R.string.validation_rating)
    ValidationIssue.Type.TOO_MANY_METHODS -> uiPlural(R.plurals.validation_too_many_methods, limit ?: 0, limit ?: 0)
    ValidationIssue.Type.TOO_MANY_STEPS -> uiPlural(R.plurals.validation_too_many_steps, limit ?: 0, limit ?: 0)
    ValidationIssue.Type.TEMPERATURE_OUT_OF_RANGE -> uiText(R.string.validation_temperature)
    ValidationIssue.Type.DURATION_OUT_OF_RANGE -> uiText(R.string.validation_duration)
}

/** Localized text for a successful connection test. */
fun ConnectionInfo.toUiText(): UiText = when {
    serverVersion != null -> uiPlural(R.plurals.connection_server, productCount ?: 0, serverVersion, productCount ?: 0)
    productCount == null -> uiText(R.string.connection_no_library)
    else -> uiPlural(R.plurals.connection_library, productCount, productCount)
}

/**
 * Turns any failure from sync, connection tests or import/export into a readable,
 * localized message. Unknown errors fall back to the exception's own message.
 */
fun Throwable.toUiText(): UiText = when (this) {
    is RemoteConfigurationException -> uiText(
        when (reason) {
            Reason.MISSING_ACCOUNT_ID -> R.string.error_config_account_id
            Reason.MISSING_ENDPOINT -> R.string.error_config_endpoint
            Reason.INVALID_ENDPOINT -> R.string.error_config_invalid_endpoint
            Reason.MISSING_BUCKET -> R.string.error_config_bucket
            Reason.MISSING_ACCESS_KEY -> R.string.error_config_access_key
            Reason.MISSING_SECRET -> R.string.error_config_secret
            Reason.INVALID_SERVER_URL -> R.string.error_config_server_url
            Reason.MISSING_TOKEN -> R.string.error_config_token
        },
    )
    is S3Exception -> when {
        status == 401 || status == 403 -> uiText(R.string.error_access_denied)
        code == "NoSuchBucket" -> uiText(R.string.error_bucket_not_found)
        code != null -> uiText(R.string.error_storage_status_code, status, code)
        else -> uiText(R.string.error_storage_status, status)
    }
    is S3PreconditionFailed, is S3ConditionalWritesUnsupported -> uiText(R.string.error_conflict)
    is ServerException -> when (status) {
        401 -> uiText(R.string.error_invalid_token)
        404 -> uiText(R.string.error_api_not_found)
        else -> uiText(R.string.error_server_status, status, message.orEmpty())
    }
    is IncompatibleLibraryException -> uiText(R.string.error_newer_library)
    is NewerLibraryFileException -> uiText(R.string.error_newer_file)
    is ValidationException -> {
        val first = issues.firstOrNull()?.toUiText() ?: uiText(R.string.error_unknown)
        if (productName != null) uiText(R.string.validation_in_product, productName, first) else first
    }
    is LibraryFileException -> uiText(
        when (reason) {
            LibraryFileException.Reason.CANNOT_READ -> R.string.error_file_read
            LibraryFileException.Reason.CANNOT_WRITE -> R.string.error_file_write
            LibraryFileException.Reason.NOT_AN_EXPORT -> R.string.error_not_an_export
        },
    )
    is java.net.UnknownHostException ->
        uiText(R.string.error_unknown_host, message?.takeIf { it.isNotBlank() } ?: uiText(R.string.error_the_server))
    is java.net.ConnectException -> uiText(R.string.error_connection_refused)
    is java.net.SocketTimeoutException -> uiText(R.string.error_timeout)
    is javax.net.ssl.SSLException -> uiText(R.string.error_tls, message.orEmpty())
    else -> message?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) } ?: uiText(R.string.error_unknown)
}
