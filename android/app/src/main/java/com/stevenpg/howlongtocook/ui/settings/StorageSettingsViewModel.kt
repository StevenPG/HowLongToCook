package com.stevenpg.howlongtocook.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stevenpg.howlongtocook.data.remote.RemoteStoreFactory
import com.stevenpg.howlongtocook.data.repository.ProductRepository
import com.stevenpg.howlongtocook.data.settings.S3Provider
import com.stevenpg.howlongtocook.data.settings.S3Settings
import com.stevenpg.howlongtocook.data.settings.SecretKind
import com.stevenpg.howlongtocook.data.settings.SecretStore
import com.stevenpg.howlongtocook.data.settings.ServerSettings
import com.stevenpg.howlongtocook.data.settings.SettingsRepository
import com.stevenpg.howlongtocook.data.settings.StorageMode
import com.stevenpg.howlongtocook.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The five destinations shown to the user (S3 modes are split by provider). */
enum class StorageOption(val title: String, val subtitle: String) {
    LOCAL("This device only", "No sync. Use export/import for backups."),
    AWS("Amazon S3", "Your own S3 bucket and IAM access key."),
    R2("Cloudflare R2", "An R2 bucket with an S3 API token."),
    S3_COMPATIBLE("S3-compatible", "MinIO, Garage, Backblaze B2, Wasabi…"),
    SERVER("Self-hosted server", "The HowLongToCook Rust server."),
}

sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data class Ok(val message: String) : TestState
    data class Failed(val message: String) : TestState
}

data class StorageUiState(
    val loading: Boolean = true,
    val option: StorageOption = StorageOption.LOCAL,
    val s3: S3Settings = S3Settings(),
    val s3Secret: String = "",
    val hasSavedS3Secret: Boolean = false,
    val serverUrl: String = "",
    val serverToken: String = "",
    val hasSavedServerToken: Boolean = false,
    val test: TestState = TestState.Idle,
    val saved: Boolean = false,
)

@HiltViewModel
class StorageSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
    private val factory: RemoteStoreFactory,
    private val repository: ProductRepository,
    private val scheduler: SyncScheduler,
) : ViewModel() {
    private val _state = MutableStateFlow(StorageUiState())
    val state: StateFlow<StorageUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = settings.current()
            val option = when (s.storageMode) {
                StorageMode.LOCAL -> StorageOption.LOCAL
                StorageMode.SERVER -> StorageOption.SERVER
                StorageMode.R2 -> StorageOption.R2
                StorageMode.S3 -> if (s.s3.provider == S3Provider.CUSTOM) StorageOption.S3_COMPATIBLE else StorageOption.AWS
            }
            _state.value = StorageUiState(
                loading = false,
                option = option,
                s3 = s.s3,
                hasSavedS3Secret = secrets.has(SecretKind.S3_SECRET_ACCESS_KEY),
                serverUrl = s.server.baseUrl,
                hasSavedServerToken = secrets.has(SecretKind.SERVER_TOKEN),
            )
        }
    }

    fun select(option: StorageOption) = _state.update { s ->
        val provider = when (option) {
            StorageOption.AWS -> S3Provider.AWS
            StorageOption.R2 -> S3Provider.R2
            StorageOption.S3_COMPATIBLE -> S3Provider.CUSTOM
            else -> s.s3.provider
        }
        s.copy(option = option, s3 = s.s3.copy(provider = provider), test = TestState.Idle)
    }

    fun updateS3(f: (S3Settings) -> S3Settings) = _state.update { it.copy(s3 = f(it.s3), test = TestState.Idle) }
    fun setS3Secret(v: String) = _state.update { it.copy(s3Secret = v, test = TestState.Idle) }
    fun setServerUrl(v: String) = _state.update { it.copy(serverUrl = v, test = TestState.Idle) }
    fun setServerToken(v: String) = _state.update { it.copy(serverToken = v, test = TestState.Idle) }

    fun test() {
        val s = state.value
        _state.update { it.copy(test = TestState.Running) }
        viewModelScope.launch {
            val result = try {
                val store = when (s.option) {
                    StorageOption.LOCAL -> null
                    StorageOption.SERVER -> factory.server(
                        ServerSettings(s.serverUrl),
                        s.serverToken.ifBlank { secrets.get(SecretKind.SERVER_TOKEN) },
                    )
                    else -> factory.s3(s.s3, s.s3Secret.ifBlank { secrets.get(SecretKind.S3_SECRET_ACCESS_KEY) })
                }
                TestState.Ok(store?.testConnection() ?: "Nothing to test in local-only mode")
            } catch (e: Exception) {
                TestState.Failed(e.message ?: e::class.simpleName ?: "Connection failed")
            }
            _state.update { it.copy(test = result) }
        }
    }

    fun save() {
        val s = state.value
        viewModelScope.launch {
            val mode = when (s.option) {
                StorageOption.LOCAL -> StorageMode.LOCAL
                StorageOption.SERVER -> StorageMode.SERVER
                StorageOption.R2 -> StorageMode.R2
                StorageOption.AWS, StorageOption.S3_COMPATIBLE -> StorageMode.S3
            }
            if (s.s3Secret.isNotBlank()) secrets.put(SecretKind.S3_SECRET_ACCESS_KEY, s.s3Secret.trim())
            if (s.serverToken.isNotBlank()) secrets.put(SecretKind.SERVER_TOKEN, s.serverToken.trim())
            val changed = settings.saveRemote(mode, s.s3, ServerSettings(s.serverUrl))
            // A new destination must receive the full library, not just pending edits.
            if (changed && mode != StorageMode.LOCAL) repository.markAllForUpload()
            if (mode != StorageMode.LOCAL) scheduler.syncNow()
            _state.update { it.copy(saved = true) }
        }
    }
}
