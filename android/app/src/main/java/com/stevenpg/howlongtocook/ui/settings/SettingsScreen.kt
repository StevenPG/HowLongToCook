package com.stevenpg.howlongtocook.ui.settings

import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stevenpg.howlongtocook.R
import com.stevenpg.howlongtocook.BuildConfig
import com.stevenpg.howlongtocook.data.settings.AppSettings
import com.stevenpg.howlongtocook.data.settings.S3Provider
import com.stevenpg.howlongtocook.data.settings.StorageMode
import com.stevenpg.howlongtocook.data.settings.ThemeMode
import com.stevenpg.howlongtocook.data.settings.UnitPreference
import com.stevenpg.howlongtocook.ui.components.SectionHeader

private const val SOURCE_URL = "https://github.com/StevenPG/HowLongToCook"

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenStorage: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it.resolve(resources))
            viewModel.messageShown()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            androidx.compose.material3.LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val s = settings ?: return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.settings_storage_sync))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_storage)) },
                supportingContent = { Text(storageSummary(s)) },
                leadingContent = { Icon(Icons.Outlined.Cloud, null) },
                modifier = Modifier.clickable(onClick = onOpenStorage),
            )
            if (s.storageMode != StorageMode.LOCAL) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_sync_now)) },
                    supportingContent = {
                        val err = s.sync.lastError
                        Text(
                            when {
                                syncing -> stringResource(R.string.sync_syncing)
                                err != null -> stringResource(R.string.settings_last_failed, err)
                                s.sync.lastSuccessAt != null -> stringResource(R.string.settings_last_synced, DateUtils.getRelativeTimeSpanString(s.sync.lastSuccessAt).toString())
                                else -> stringResource(R.string.settings_not_synced)
                            },
                            color = if (err != null && !syncing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.Sync, null) },
                    trailingContent = { if (syncing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) },
                    modifier = Modifier.clickable(enabled = !syncing, onClick = viewModel::syncNow),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader(stringResource(R.string.settings_appearance))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_theme)) },
                leadingContent = { Icon(Icons.Outlined.DarkMode, null) },
            )
            Segmented(
                options = ThemeMode.entries,
                selected = s.themeMode,
                label = { when (it) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark } },
                onSelect = viewModel::setTheme,
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                supportingContent = { Text(stringResource(R.string.settings_dynamic_color_summary)) },
                leadingContent = { Icon(Icons.Outlined.Palette, null) },
                trailingContent = { Switch(checked = s.dynamicColor, onCheckedChange = viewModel::setDynamicColor) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_temperature_unit)) },
                leadingContent = { Icon(Icons.Outlined.Thermostat, null) },
            )
            Segmented(
                options = UnitPreference.entries,
                selected = s.unit,
                label = { when (it) { UnitPreference.AUTO -> R.string.unit_auto; UnitPreference.FAHRENHEIT -> R.string.unit_fahrenheit; UnitPreference.CELSIUS -> R.string.unit_celsius } },
                onSelect = viewModel::setUnit,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader(stringResource(R.string.settings_lookup))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_open_food_facts)) },
                supportingContent = { Text(stringResource(R.string.settings_open_food_facts_summary)) },
                leadingContent = { Icon(Icons.Outlined.TravelExplore, null) },
                trailingContent = { Switch(checked = s.openFoodFacts, onCheckedChange = viewModel::setOpenFoodFacts) },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader(stringResource(R.string.settings_backup))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_export)) },
                supportingContent = { Text(stringResource(R.string.settings_export_summary)) },
                leadingContent = { Icon(Icons.Outlined.Download, null) },
                modifier = Modifier.clickable { exportLauncher.launch(viewModel.suggestedExportName()) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_import)) },
                supportingContent = { Text(stringResource(R.string.settings_import_summary)) },
                leadingContent = { Icon(Icons.Outlined.Upload, null) },
                modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader(stringResource(R.string.settings_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name)) },
                supportingContent = { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
                leadingContent = { Icon(Icons.Outlined.Info, null) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_license)) },
                supportingContent = { Text(stringResource(R.string.settings_license_summary)) },
                leadingContent = { Icon(Icons.Outlined.Gavel, null) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_source)) },
                supportingContent = { Text(SOURCE_URL.removePrefix("https://")) },
                leadingContent = { Icon(Icons.Outlined.Code, null) },
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri()))
                },
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(24.dp))
        }
    }
}

@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> Int, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp, bottom = 8.dp)) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = o == selected,
                onClick = { onSelect(o) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(stringResource(label(o))) }
        }
    }
}

@Composable
private fun storageSummary(s: AppSettings): String = when (s.storageMode) {
    StorageMode.LOCAL -> stringResource(R.string.storage_summary_local)
    StorageMode.SERVER -> stringResource(R.string.storage_summary_server, s.server.baseUrl.removePrefix("https://").removePrefix("http://"))
    StorageMode.R2 -> stringResource(R.string.storage_summary_r2, s.s3.bucket)
    StorageMode.S3 -> stringResource(
        if (s.s3.provider == S3Provider.CUSTOM) R.string.storage_summary_custom else R.string.storage_summary_aws,
        s.s3.bucket,
    )
}
