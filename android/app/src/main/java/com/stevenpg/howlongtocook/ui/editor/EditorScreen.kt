package com.stevenpg.howlongtocook.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stevenpg.howlongtocook.data.model.MethodType
import com.stevenpg.howlongtocook.data.model.StepAction
import com.stevenpg.howlongtocook.data.model.TemperatureUnit
import com.stevenpg.howlongtocook.ui.components.IconBadge
import com.stevenpg.howlongtocook.ui.components.RatingBar
import com.stevenpg.howlongtocook.ui.components.SectionHeader
import com.stevenpg.howlongtocook.ui.components.icon
import com.stevenpg.howlongtocook.util.label

@Composable
fun EditorScreen(
    scannedBarcode: String?,
    onScannedBarcodeConsumed: () -> Unit,
    onScanBarcode: () -> Unit,
    onClose: () -> Unit,
    onSaved: (id: String, isNew: Boolean) -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(scannedBarcode) {
        if (scannedBarcode != null) {
            viewModel.addBarcode(scannedBarcode)
            onScannedBarcodeConsumed()
        }
    }
    LaunchedEffect(state.savedId) {
        state.savedId?.let { onSaved(it, state.isNew) }
    }

    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val requestClose = { if (state.changed) confirmDiscard = true else onClose() }
    BackHandler(enabled = state.changed) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New product" else "Edit product") },
                navigationIcon = {
                    IconButton(onClick = requestClose) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::save,
                        enabled = !state.saving && !state.loading,
                        modifier = Modifier.testTag("save_button"),
                    ) { Text("Save") }
                },
            )
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            if (state.errors.isNotEmpty()) {
                item { ErrorCard(state.errors) }
            }
            item { DetailsSection(state, viewModel) }
            item { BarcodeSection(state, viewModel, onScanBarcode) }
            item { SectionHeader("Cooking instructions", Modifier.padding(top = 12.dp)) }
            if (state.methods.isEmpty()) {
                item {
                    Text(
                        "Add at least one method from the box.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(state.methods, key = { it.key }) { method ->
                MethodEditor(
                    method = method,
                    index = state.methods.indexOf(method),
                    count = state.methods.size,
                    viewModel = viewModel,
                    modifier = Modifier.animateItem(),
                )
            }
            item { AddMethodRow(onAdd = viewModel::addMethod) }
            item { MoreSection(state, viewModel) }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to this product haven't been saved.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun ErrorCard(errors: List<String>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(Icons.Outlined.ErrorOutline, null)
            Spacer(Modifier.width(12.dp))
            Column { errors.forEach { Text(it) } }
        }
    }
}

@Composable
private fun DetailsSection(state: EditorUiState, vm: EditorViewModel) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = state.name,
            onValueChange = vm::setName,
            label = { Text("Name") },
            placeholder = { Text("e.g. Chicken pot pie") },
            isError = state.nameError,
            supportingText = if (state.nameError) ({ Text("Required") }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().testTag("name_field"),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.brand,
            onValueChange = vm::setBrand,
            label = { Text("Brand (optional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.lookingUp) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Looking up barcode…", style = MaterialTheme.typography.bodySmall)
            }
        }
        state.lookupMessage?.let {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BarcodeSection(state: EditorUiState, vm: EditorViewModel, onScan: () -> Unit) {
    SectionHeader("Barcodes", Modifier.padding(top = 12.dp))
    Column(Modifier.padding(horizontal = 16.dp)) {
        if (state.barcodes.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.barcodes.forEach { code ->
                    InputChip(
                        selected = false,
                        onClick = { vm.removeBarcode(code) },
                        label = { Text(code) },
                        trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = "Remove $code", Modifier.size(18.dp)) },
                    )
                }
            }
        }
        OutlinedTextField(
            value = state.barcodeInput,
            onValueChange = vm::setBarcodeInput,
            label = { Text("Add barcode") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.addBarcode() }),
            trailingIcon = {
                Row {
                    if (state.barcodeInput.isNotBlank()) {
                        IconButton(onClick = { vm.addBarcode() }) { Icon(Icons.Outlined.Add, contentDescription = "Add barcode") }
                    }
                    IconButton(onClick = onScan) { Icon(Icons.Outlined.QrCodeScanner, contentDescription = "Scan barcode") }
                }
            },
            supportingText = state.barcodeWarning?.let { w ->
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Warning, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(w)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MethodEditor(
    method: MethodDraft,
    index: Int,
    count: Int,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(method.type.icon, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(method.type.label(), style = MaterialTheme.typography.titleMedium)
                    if (index == 0 && count > 1) {
                        Text("Preferred", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Method options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (index > 0) {
                            DropdownMenuItem(
                                text = { Text("Make preferred") },
                                leadingIcon = { Icon(Icons.Outlined.ArrowUpward, null) },
                                onClick = { menu = false; repeat(index) { viewModel.moveMethod(method.key, up = true) } },
                            )
                        }
                        if (index < count - 1) {
                            DropdownMenuItem(
                                text = { Text("Move down") },
                                leadingIcon = { Icon(Icons.Outlined.ArrowDownward, null) },
                                onClick = { menu = false; viewModel.moveMethod(method.key, up = false) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Remove method") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = { menu = false; viewModel.removeMethod(method.key) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (method.usesTemperature) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = method.temperature,
                        onValueChange = { v -> viewModel.updateMethod(method.key) { it.copy(temperature = v.filter(Char::isDigit).take(4)) } },
                        label = { Text("Temperature") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    SingleChoiceSegmentedButtonRow {
                        TemperatureUnit.entries.forEachIndexed { i, u ->
                            SegmentedButton(
                                selected = method.unit == u,
                                onClick = { viewModel.updateMethod(method.key) { it.copy(unit = u) } },
                                shape = SegmentedButtonDefaults.itemShape(i, TemperatureUnit.entries.size),
                                icon = {},
                            ) { Text("°${u.name}") }
                        }
                    }
                }
                ListItem(
                    headlineContent = { Text("Preheat") },
                    trailingContent = {
                        Switch(
                            checked = method.preheat,
                            onCheckedChange = { c -> viewModel.updateMethod(method.key) { it.copy(preheat = c) } },
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
            } else {
                OutlinedTextField(
                    value = method.power,
                    onValueChange = { v -> viewModel.updateMethod(method.key) { it.copy(power = v.take(60)) } },
                    label = { Text(if (method.type == MethodType.MICROWAVE) "Power level" else "Heat") },
                    placeholder = { Text(if (method.type == MethodType.MICROWAVE) "High, 50%…" else "Medium-high…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("Steps", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            method.steps.forEachIndexed { i, step ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                StepEditor(
                    number = i + 1,
                    step = step,
                    canMoveUp = i > 0,
                    canMoveDown = i < method.steps.size - 1,
                    onChange = { f -> viewModel.updateStep(method.key, step.key, f) },
                    onMove = { up -> viewModel.moveStep(method.key, step.key, up) },
                    onRemove = { viewModel.removeStep(method.key, step.key) },
                )
            }
            TextButton(onClick = { viewModel.addStep(method.key) }) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add step")
            }
            OutlinedTextField(
                value = method.notes,
                onValueChange = { v -> viewModel.updateMethod(method.key) { it.copy(notes = v) } },
                label = { Text("Method notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StepEditor(
    number: Int,
    step: StepDraft,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: ((StepDraft) -> StepDraft) -> Unit,
    onMove: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    var actionMenu by remember { mutableStateOf(false) }
    var optionsMenu by remember { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$number.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(28.dp))
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { actionMenu = true }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(step.action.icon, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(step.action.label(), maxLines = 1)
                    Icon(Icons.Outlined.ArrowDropDown, null)
                }
                DropdownMenu(expanded = actionMenu, onDismissRequest = { actionMenu = false }) {
                    StepAction.entries.forEach { a ->
                        DropdownMenuItem(
                            text = { Text(a.label()) },
                            leadingIcon = { Icon(a.icon, null) },
                            onClick = { actionMenu = false; onChange { it.copy(action = a) } },
                        )
                    }
                }
            }
            DurationField(step.minutes, "min") { v -> onChange { it.copy(minutes = v) } }
            Spacer(Modifier.width(6.dp))
            DurationField(step.seconds, "sec") { v -> onChange { it.copy(seconds = v) } }
            Box {
                IconButton(onClick = { optionsMenu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Step $number options") }
                DropdownMenu(expanded = optionsMenu, onDismissRequest = { optionsMenu = false }) {
                    if (canMoveUp) {
                        DropdownMenuItem(text = { Text("Move up") }, leadingIcon = { Icon(Icons.Outlined.ArrowUpward, null) }, onClick = { optionsMenu = false; onMove(true) })
                    }
                    if (canMoveDown) {
                        DropdownMenuItem(text = { Text("Move down") }, leadingIcon = { Icon(Icons.Outlined.ArrowDownward, null) }, onClick = { optionsMenu = false; onMove(false) })
                    }
                    DropdownMenuItem(text = { Text("Remove step") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { optionsMenu = false; onRemove() })
                }
            }
        }
        OutlinedTextField(
            value = step.note,
            onValueChange = { v -> onChange { it.copy(note = v.take(500)) } },
            placeholder = { Text(if (step.action == StepAction.OTHER) "Describe the step" else "Note (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(start = 28.dp, top = 4.dp),
        )
    }
}

@Composable
private fun DurationField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(if (label == "sec") 2 else 4)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = Modifier.width(72.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddMethodRow(onAdd: (MethodType) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        MethodType.entries.forEach { type ->
            AssistChip(
                onClick = { onAdd(type) },
                label = { Text(type.label()) },
                leadingIcon = { Icon(type.icon, null, Modifier.size(18.dp)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoreSection(state: EditorUiState, vm: EditorViewModel) {
    SectionHeader("More", Modifier.padding(top = 16.dp))
    Column(Modifier.padding(horizontal = 16.dp)) {
        if (state.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { vm.removeTag(tag) },
                        label = { Text(tag) },
                        trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = "Remove $tag", Modifier.size(18.dp)) },
                    )
                }
            }
        }
        OutlinedTextField(
            value = state.tagInput,
            onValueChange = vm::setTagInput,
            label = { Text("Add tags") },
            placeholder = { Text("dinner, kids, spicy") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.addTag() }),
            trailingIcon = {
                if (state.tagInput.isNotBlank()) {
                    IconButton(onClick = { vm.addTag() }) { Icon(Icons.Outlined.Add, contentDescription = "Add tag") }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        ListItem(
            headlineContent = { Text("Rating") },
            trailingContent = { RatingBar(state.rating, onRate = vm::setRating, starSize = 28.dp) },
        )
        ListItem(
            headlineContent = { Text("Favorite") },
            trailingContent = { Switch(checked = state.favorite, onCheckedChange = vm::setFavorite) },
        )
        OutlinedTextField(
            value = state.notes,
            onValueChange = vm::setNotes,
            label = { Text("Notes") },
            placeholder = { Text("Anything worth remembering") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
