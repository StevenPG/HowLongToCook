package com.stevenpg.howlongtocook.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.stevenpg.howlongtocook.R
import com.stevenpg.howlongtocook.ui.text.UiText
import com.stevenpg.howlongtocook.ui.text.asString
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun StorageSettingsScreen(
    onBack: () -> Unit,
    viewModel: StorageSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_storage)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.storage_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            StorageOption.entries.forEach { option ->
                OptionCard(option, selected = state.option == option) { viewModel.select(option) }
            }
            Spacer(Modifier.height(16.dp))
            when (state.option) {
                StorageOption.LOCAL -> Unit
                StorageOption.SERVER -> ServerForm(state, viewModel)
                else -> S3Form(state, viewModel)
            }
            Spacer(Modifier.height(16.dp))
            TestResult(state.test)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                if (state.option != StorageOption.LOCAL) {
                    OutlinedButton(onClick = viewModel::test, enabled = state.test != TestState.Running, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.storage_test))
                    }
                }
                Button(onClick = viewModel::save, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}

@Composable
private fun OptionCard(option: StorageOption, selected: Boolean, onSelect: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(option.title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(option.subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun S3Form(state: StorageUiState, vm: StorageSettingsViewModel) {
    val s3 = state.s3
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state.option) {
            StorageOption.R2 -> Field(stringResource(R.string.storage_account_id), s3.accountId, stringResource(R.string.storage_account_id_hint)) { v -> vm.updateS3 { it.copy(accountId = v) } }
            StorageOption.S3_COMPATIBLE -> {
                Field(stringResource(R.string.storage_endpoint), s3.endpoint, stringResource(R.string.storage_endpoint_hint), KeyboardType.Uri) { v -> vm.updateS3 { it.copy(endpoint = v) } }
                Field(stringResource(R.string.storage_region), s3.region, stringResource(R.string.storage_region_hint_custom)) { v -> vm.updateS3 { it.copy(region = v) } }
            }
            else -> Field(stringResource(R.string.storage_region), s3.region, stringResource(R.string.storage_region_hint_aws)) { v -> vm.updateS3 { it.copy(region = v) } }
        }
        Field(stringResource(R.string.storage_bucket), s3.bucket) { v -> vm.updateS3 { it.copy(bucket = v) } }
        Field(stringResource(R.string.storage_access_key), s3.accessKeyId) { v -> vm.updateS3 { it.copy(accessKeyId = v) } }
        SecretField(
            label = stringResource(R.string.storage_secret_key),
            value = state.s3Secret,
            hasSaved = state.hasSavedS3Secret,
            onChange = vm::setS3Secret,
        )
        Field(stringResource(R.string.storage_prefix), s3.prefix, stringResource(R.string.storage_prefix_hint, s3.objectKey())) { v -> vm.updateS3 { it.copy(prefix = v) } }
        if (state.option == StorageOption.AWS) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.storage_path_style), Modifier.weight(1f))
                Switch(checked = s3.pathStyle, onCheckedChange = { c -> vm.updateS3 { it.copy(pathStyle = c) } })
            }
        }
        if (state.option == StorageOption.R2) {
            Text(
                stringResource(R.string.storage_r2_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ServerForm(state: StorageUiState, vm: StorageSettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(stringResource(R.string.storage_server_url), state.serverUrl, stringResource(R.string.storage_server_url_hint), KeyboardType.Uri, onChange = vm::setServerUrl)
        if (state.serverUrl.trim().startsWith("http://")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Warning, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.storage_http_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        SecretField(stringResource(R.string.storage_token), state.serverToken, state.hasSavedServerToken, vm::setServerToken)
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    supporting: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SecretField(label: String, value: String, hasSaved: Boolean, onChange: (String) -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (hasSaved) ({ Text(stringResource(R.string.storage_secret_saved)) }) else null,
        supportingText = { Text(stringResource(R.string.storage_secret_help)) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) stringResource(R.string.action_hide) else stringResource(R.string.action_show),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TestResult(test: TestState) {
    when (test) {
        TestState.Idle -> Unit
        TestState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.storage_testing))
        }
        is TestState.Ok -> ResultCard(test.message, ok = true)
        is TestState.Failed -> ResultCard(test.message, ok = false)
    }
}

@Composable
private fun ResultCard(message: UiText, ok: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
            contentColor = if (ok) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null)
            Spacer(Modifier.width(10.dp))
            Text(message.asString())
        }
    }
}
