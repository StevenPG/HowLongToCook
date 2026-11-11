package com.stevenpg.howlongtocook.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stevenpg.howlongtocook.data.model.Product
import com.stevenpg.howlongtocook.ui.components.EmptyState
import com.stevenpg.howlongtocook.ui.components.Pill
import com.stevenpg.howlongtocook.ui.components.RatingBar
import com.stevenpg.howlongtocook.ui.components.icon
import com.stevenpg.howlongtocook.util.label

@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onCookMode: (String, Int) -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    DetailContent(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onCookMode = onCookMode,
        onSelectMethod = viewModel::selectMethod,
        onToggleFavorite = viewModel::toggleFavorite,
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailContent(
    state: DetailUiState,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onCookMode: (String, Int) -> Unit,
    onSelectMethod: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    val product = state.product
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (product != null) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                if (product.favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                                contentDescription = if (product.favorite) "Remove from favorites" else "Add to favorites",
                                tint = if (product.favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onEdit(product.id) }) { Icon(Icons.Outlined.Edit, contentDescription = "Edit") }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                    onClick = { menuOpen = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when {
            state.loading -> Unit
            product == null -> EmptyState(
                icon = Icons.Outlined.Restaurant,
                title = "Product not found",
                message = "It may have been deleted on another device.",
                modifier = Modifier.padding(padding),
            ) { OutlinedButton(onClick = onBack) { Text("Back to library") } }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Header(product)
                Spacer(Modifier.height(20.dp))
                if (product.methods.isEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(20.dp).fillMaxWidth()) {
                            Text("No cooking instructions yet", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Add the microwave, oven, air fryer or stovetop steps from the box.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { onEdit(product.id) }) { Text("Add instructions") }
                        }
                    }
                } else {
                    if (product.methods.size > 1) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            product.methods.forEachIndexed { i, m ->
                                SegmentedButton(
                                    selected = state.selectedMethod == i,
                                    onClick = { onSelectMethod(i) },
                                    shape = SegmentedButtonDefaults.itemShape(i, product.methods.size),
                                    icon = { Icon(m.type.icon, null, Modifier.size(18.dp)) },
                                ) { Text(m.type.label(), maxLines = 1) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    MethodCard(product.methods[state.selectedMethod])
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onCookMode(product.id, state.selectedMethod) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Icons.Outlined.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cook mode", style = MaterialTheme.typography.titleMedium)
                    }
                }
                product.notes?.let { notes ->
                    Spacer(Modifier.height(16.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Row(Modifier.padding(16.dp).fillMaxWidth()) {
                            Icon(Icons.AutoMirrored.Outlined.StickyNote2, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(Modifier.width(12.dp))
                            Text(notes, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
                if (product.barcodes.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        product.barcodes.forEach {
                            Pill(
                                it,
                                icon = Icons.Outlined.QrCode2,
                                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                                content = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (confirmDelete && product != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text("Delete \"${product.name}\"?") },
            text = { Text("It will be removed from this device and from your synced storage.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(product: Product) {
    Column {
        Text(product.name, style = MaterialTheme.typography.headlineMedium)
        product.brand?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (product.rating != null || product.tags.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                product.rating?.let { RatingBar(it) }
                product.tags.forEach { Pill(it) }
            }
        }
    }
}
