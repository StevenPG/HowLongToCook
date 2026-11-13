package com.stevenpg.howlongtocook.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository internal constructor(private val store: DataStore<Preferences>) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context.settingsStore)

    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val unit = stringPreferencesKey("unit")
        val off = booleanPreferencesKey("open_food_facts")
        val storage = stringPreferencesKey("storage_mode")
        val s3Provider = stringPreferencesKey("s3_provider")
        val s3Endpoint = stringPreferencesKey("s3_endpoint")
        val s3Account = stringPreferencesKey("s3_account_id")
        val s3Region = stringPreferencesKey("s3_region")
        val s3Bucket = stringPreferencesKey("s3_bucket")
        val s3Prefix = stringPreferencesKey("s3_prefix")
        val s3AccessKey = stringPreferencesKey("s3_access_key_id")
        val s3PathStyle = booleanPreferencesKey("s3_path_style")
        val serverUrl = stringPreferencesKey("server_url")
        val lastSuccess = longPreferencesKey("sync_last_success")
        val lastAttempt = longPreferencesKey("sync_last_attempt")
        val lastError = stringPreferencesKey("sync_last_error")
        val cursor = stringPreferencesKey("sync_cursor")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            themeMode = p[Keys.theme].toEnum(ThemeMode.SYSTEM),
            dynamicColor = p[Keys.dynamic] ?: false,
            unit = p[Keys.unit].toEnum(UnitPreference.AUTO),
            openFoodFacts = p[Keys.off] ?: true,
            storageMode = p[Keys.storage].toEnum(StorageMode.LOCAL),
            s3 = S3Settings(
                provider = p[Keys.s3Provider].toEnum(S3Provider.AWS),
                endpoint = p[Keys.s3Endpoint] ?: "",
                accountId = p[Keys.s3Account] ?: "",
                region = p[Keys.s3Region] ?: "us-east-1",
                bucket = p[Keys.s3Bucket] ?: "",
                prefix = p[Keys.s3Prefix] ?: "howlongtocook",
                accessKeyId = p[Keys.s3AccessKey] ?: "",
                pathStyle = p[Keys.s3PathStyle] ?: false,
            ),
            server = ServerSettings(baseUrl = p[Keys.serverUrl] ?: ""),
            sync = SyncStatus(
                lastSuccessAt = p[Keys.lastSuccess],
                lastAttemptAt = p[Keys.lastAttempt],
                lastError = p[Keys.lastError],
            ),
            syncCursor = p[Keys.cursor],
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[Keys.dynamic] = enabled }
    suspend fun setUnit(unit: UnitPreference) = store.edit { it[Keys.unit] = unit.name }
    suspend fun setOpenFoodFacts(enabled: Boolean) = store.edit { it[Keys.off] = enabled }

    /**
     * Saves the remote configuration. Returns true when the destination changed, in which
     * case the caller must re-upload everything (see [com.stevenpg.howlongtocook.data.sync.SyncEngine]).
     */
    suspend fun saveRemote(mode: StorageMode, s3: S3Settings, server: ServerSettings): Boolean {
        val before = current()
        val destinationChanged = before.storageMode != mode ||
            (mode in setOf(StorageMode.S3, StorageMode.R2) && before.s3.destinationKey() != s3.destinationKey()) ||
            (mode == StorageMode.SERVER && before.server.baseUrl.trimEnd('/') != server.baseUrl.trimEnd('/'))
        store.edit {
            it[Keys.storage] = mode.name
            it[Keys.s3Provider] = s3.provider.name
            it[Keys.s3Endpoint] = s3.endpoint.trim()
            it[Keys.s3Account] = s3.accountId.trim()
            it[Keys.s3Region] = s3.region.trim()
            it[Keys.s3Bucket] = s3.bucket.trim()
            it[Keys.s3Prefix] = s3.prefix.trim()
            it[Keys.s3AccessKey] = s3.accessKeyId.trim()
            it[Keys.s3PathStyle] = s3.pathStyle
            it[Keys.serverUrl] = server.baseUrl.trim().trimEnd('/')
            if (destinationChanged) {
                it.remove(Keys.cursor)
                it.remove(Keys.lastSuccess)
                it.remove(Keys.lastError)
            }
        }
        return destinationChanged
    }

    suspend fun recordSyncAttempt(at: Long) = store.edit { it[Keys.lastAttempt] = at }

    suspend fun recordSyncSuccess(at: Long, cursor: String?) = store.edit {
        it[Keys.lastSuccess] = at
        it[Keys.lastAttempt] = at
        it.remove(Keys.lastError)
        if (cursor != null) it[Keys.cursor] = cursor else it.remove(Keys.cursor)
    }

    suspend fun recordSyncFailure(at: Long, message: String) = store.edit {
        it[Keys.lastAttempt] = at
        it[Keys.lastError] = message
    }

    private fun S3Settings.destinationKey() =
        listOf(provider, resolvedEndpoint(), bucket.trim(), objectKey()).joinToString("|")
}

private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
