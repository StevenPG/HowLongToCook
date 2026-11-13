package com.stevenpg.howlongtocook.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stevenpg.howlongtocook.R
import com.stevenpg.howlongtocook.data.settings.AppSettings
import com.stevenpg.howlongtocook.data.settings.SettingsRepository
import com.stevenpg.howlongtocook.data.settings.ThemeMode
import com.stevenpg.howlongtocook.data.settings.UnitPreference
import com.stevenpg.howlongtocook.data.sync.SyncScheduler
import com.stevenpg.howlongtocook.data.transfer.LibraryFiles
import com.stevenpg.howlongtocook.ui.text.UiText
import com.stevenpg.howlongtocook.ui.text.toUiText
import com.stevenpg.howlongtocook.ui.text.uiPlural
import com.stevenpg.howlongtocook.ui.text.uiText
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

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    fun setUnit(unit: UnitPreference) = viewModelScope.launch { settingsRepository.setUnit(unit) }
    fun setOpenFoodFacts(enabled: Boolean) = viewModelScope.launch { settingsRepository.setOpenFoodFacts(enabled) }
    fun syncNow() = scheduler.syncNow()

    fun suggestedExportName(): String = files.suggestedFileName()

    fun export(uri: Uri) = viewModelScope.launch {
        _message.value = try {
            val count = files.export(uri)
            uiPlural(R.plurals.export_done, count, count)
        } catch (e: Exception) {
            uiText(R.string.export_failed, e.toUiText())
        }
    }

    fun import(uri: Uri) = viewModelScope.launch {
        _message.value = try {
            val r = files.import(uri)
            uiPlural(R.plurals.import_done, r.received, r.received, r.applied, r.skipped)
        } catch (e: Exception) {
            uiText(R.string.import_failed, e.toUiText())
        }
    }

    fun messageShown() {
        _message.value = null
    }
}
