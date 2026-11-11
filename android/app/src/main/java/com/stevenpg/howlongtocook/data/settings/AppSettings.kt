package com.stevenpg.howlongtocook.data.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class UnitPreference { AUTO, FAHRENHEIT, CELSIUS }

enum class StorageMode { LOCAL, S3, R2, SERVER }

/** Which S3 flavour; R2 and "other" use path-style addressing. */
enum class S3Provider { AWS, R2, CUSTOM }

data class S3Settings(
    val provider: S3Provider = S3Provider.AWS,
    /** Custom endpoint for S3-compatible services (MinIO, Garage, …). */
    val endpoint: String = "",
    /** Cloudflare account ID, used to build the R2 endpoint. */
    val accountId: String = "",
    val region: String = "us-east-1",
    val bucket: String = "",
    val prefix: String = "howlongtocook",
    val accessKeyId: String = "",
    val pathStyle: Boolean = false,
) {
    /** Endpoint URL without a trailing slash. */
    fun resolvedEndpoint(): String = when (provider) {
        S3Provider.AWS -> "https://s3.${region.ifBlank { "us-east-1" }}.amazonaws.com"
        S3Provider.R2 -> "https://${accountId.trim()}.r2.cloudflarestorage.com"
        S3Provider.CUSTOM -> endpoint.trim().trimEnd('/')
    }

    fun resolvedRegion(): String = when (provider) {
        S3Provider.R2 -> "auto"
        else -> region.trim().ifBlank { "us-east-1" }
    }

    fun usePathStyle(): Boolean = provider != S3Provider.AWS || pathStyle

    fun objectKey(): String {
        val p = prefix.trim().trim('/')
        return if (p.isEmpty()) "library.json" else "$p/library.json"
    }
}

data class ServerSettings(val baseUrl: String = "")

data class SyncStatus(
    val lastSuccessAt: Long? = null,
    val lastAttemptAt: Long? = null,
    val lastError: String? = null,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val unit: UnitPreference = UnitPreference.AUTO,
    val openFoodFacts: Boolean = true,
    val storageMode: StorageMode = StorageMode.LOCAL,
    val s3: S3Settings = S3Settings(),
    val server: ServerSettings = ServerSettings(),
    val sync: SyncStatus = SyncStatus(),
    /** Opaque cursor from the remote store (server revision). */
    val syncCursor: String? = null,
)
