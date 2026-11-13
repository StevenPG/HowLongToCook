package com.stevenpg.howlongtocook.data.remote

import com.stevenpg.howlongtocook.data.remote.RemoteConfigurationException.Reason
import com.stevenpg.howlongtocook.data.remote.s3.S3Client
import com.stevenpg.howlongtocook.data.remote.s3.S3RemoteStore
import com.stevenpg.howlongtocook.data.remote.server.ServerRemoteStore
import com.stevenpg.howlongtocook.data.settings.AppSettings
import com.stevenpg.howlongtocook.data.settings.S3Provider
import com.stevenpg.howlongtocook.data.settings.S3Settings
import com.stevenpg.howlongtocook.data.settings.SecretKind
import com.stevenpg.howlongtocook.data.settings.SecretStore
import com.stevenpg.howlongtocook.data.settings.ServerSettings
import com.stevenpg.howlongtocook.data.settings.StorageMode
import io.ktor.client.HttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteStoreFactory @Inject constructor(
    private val http: HttpClient,
    private val secrets: SecretStore,
) {
    /** Returns the configured store, or null in local-only mode. */
    suspend fun create(settings: AppSettings): RemoteStore? = when (settings.storageMode) {
        StorageMode.LOCAL -> null
        StorageMode.S3, StorageMode.R2 -> s3(settings.s3, secrets.get(SecretKind.S3_SECRET_ACCESS_KEY))
        StorageMode.SERVER -> server(settings.server, secrets.get(SecretKind.SERVER_TOKEN))
    }

    fun s3(s: S3Settings, secret: String?): RemoteStore {
        if (s.provider == S3Provider.R2 && s.accountId.isBlank()) throw RemoteConfigurationException(Reason.MISSING_ACCOUNT_ID)
        if (s.provider == S3Provider.CUSTOM && s.endpoint.isBlank()) throw RemoteConfigurationException(Reason.MISSING_ENDPOINT)
        if (s.bucket.isBlank()) throw RemoteConfigurationException(Reason.MISSING_BUCKET)
        if (s.accessKeyId.isBlank()) throw RemoteConfigurationException(Reason.MISSING_ACCESS_KEY)
        if (secret.isNullOrBlank()) throw RemoteConfigurationException(Reason.MISSING_SECRET)
        val client = try {
            S3Client(
                http = http,
                endpoint = s.resolvedEndpoint(),
                bucket = s.bucket.trim(),
                region = s.resolvedRegion(),
                accessKeyId = s.accessKeyId.trim(),
                secretAccessKey = secret.trim(),
                pathStyle = s.usePathStyle(),
            )
        } catch (e: IllegalArgumentException) {
            throw RemoteConfigurationException(Reason.INVALID_ENDPOINT)
        }
        return S3RemoteStore(client, s.objectKey(), "${s.bucket}/${s.objectKey()}")
    }

    fun server(s: ServerSettings, token: String?): RemoteStore {
        val url = s.baseUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw RemoteConfigurationException(Reason.INVALID_SERVER_URL)
        }
        if (token.isNullOrBlank()) throw RemoteConfigurationException(Reason.MISSING_TOKEN)
        return ServerRemoteStore(http, url, token.trim())
    }
}
