package com.stevenpg.howlongtocook.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stevenpg.howlongtocook.data.repository.ValidationException
import com.stevenpg.howlongtocook.data.settings.AppSettings
import com.stevenpg.howlongtocook.data.settings.SettingsRepository
import com.stevenpg.howlongtocook.data.settings.ThemeMode
import com.stevenpg.howlongtocook.data.settings.UnitPreference
import com.stevenpg.howlongtocook.data.sync.SyncScheduler
import com.stevenpg.howlongtocook.data.transfer.LibraryFiles
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scheduler: SyncScheduler,
    private val files: LibraryFiles,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val syncing: StateFlow<Boolean> = scheduler.isSyncing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    fun setUnit(unit: UnitPreference) = viewModelScope.launch { settingsRepository.setUnit(unit) }
    fun setOpenFoodFacts(enabled: Boolean) = viewModelScope.launch { settingsRepository.setOpenFoodFacts(enabled) }
    fun syncNow() = scheduler.syncNow()

    fun suggestedExportName(): String = files.suggestedFileName()

    fun export(uri: Uri) = viewModelScope.launch {
        _message.value = try {
            val count = files.export(uri)
            "Exported $count product(s)"
        } catch (e: Exception) {
            "Export failed: ${e.message}"
        }
    }

    fun import(uri: Uri) = viewModelScope.launch {
        _message.value = try {
            val r = files.import(uri)
            "Imported ${r.received} product(s): ${r.applied} added or updated, ${r.skipped} already up to date"
        } catch (e: ValidationException) {
            "Import failed: ${e.errors.first()}"
        } catch (e: Exception) {
            "Import failed: ${e.message}"
        }
    }

    fun messageShown() {
        _message.value = null
    }
}
