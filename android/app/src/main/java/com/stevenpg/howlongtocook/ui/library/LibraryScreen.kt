package com.stevenpg.howlongtocook.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.stevenpg.howlongtocook.data.model.Product
import com.stevenpg.howlongtocook.ui.components.EmptyState
import com.stevenpg.howlongtocook.ui.components.IconBadge
import com.stevenpg.howlongtocook.ui.components.SectionHeader
import com.stevenpg.howlongtocook.ui.components.icon
import com.stevenpg.howlongtocook.util.summary

@Composable
fun LibraryScreen(
    onOpenProduct: (String) -> Unit,
    onAddProduct: () -> Unit,
    onScan: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LibraryContent(
        state = state,
        onQueryChange = viewModel::setQuery,
        onFilterChange = viewModel::setFilter,
        onToggleFavorite = viewModel::toggleFavorite,
        onRefresh = viewModel::syncNow,
        onOpenProduct = onOpenProduct,
        onAddProduct = onAddProduct,
        onScan = onScan,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
fun LibraryContent(
    state: LibraryUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
    onToggleFavorite: (Product) -> Unit,
    onRefresh: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onAddProduct: () -> Unit,
    onScan: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val focus = LocalFocusManager.current
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("HowLongToCook") },
                actions = {
                    SyncStatusIcon(state.sync, state.syncError)
                    IconButton(onClick = onAddProduct) { Icon(Icons.Outlined.Add, contentDescription = "Add product") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null) },
                text = { Text("Scan") },
                modifier = Modifier.testTag("scan_fab"),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.sync == SyncIndicator.SYNCING,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.loading -> Unit
                state.totalCount == 0 -> EmptyState(
                    icon = Icons.Outlined.Restaurant,
                    title = "Your freezer, organized",
                    message = "Scan a package barcode or add a product to save its cooking instructions before the box hits the recycling.",
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onScan) {
                            Icon(Icons.Outlined.QrCodeScanner, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Scan barcode")
                        }
                        OutlinedButton(onClick = onAddProduct) { Text("Add manually") }
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 96.dp),
                    modifier = Modifier.fillMaxSize().testTag("library_list"),
                ) {
                    item {
                        SearchField(state.query, onQueryChange, onClear = { onQueryChange(""); focus.clearFocus() })
                    }
                    item {
                        FilterRow(state, onFilterChange)
                    }
                    if (state.query.isBlank() && state.filter == LibraryFilter.All && state.recent.isNotEmpty()) {
                        item { SectionHeader("Recently viewed") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.recent, key = { "recent-${it.id}" }) { p ->
                                    RecentCard(p, onClick = { onOpenProduct(p.id) })
                                }
                            }
                        }
                        item { SectionHeader("All products", Modifier.padding(top = 8.dp)) }
                    }
                    if (state.products.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Outlined.SearchOff,
                                title = "No matches",
                                message = if (state.query.isNotBlank()) "Nothing matches \"${state.query.trim()}\"." else "No products in this filter yet.",
                            ) {
                                OutlinedButton(onClick = onAddProduct) { Text("Add a product") }
                            }
                        }
                    }
                    items(state.products, key = { it.id }) { p ->
                        ProductRow(
                            product = p,
                            onClick = { onOpenProduct(p.id) },
                            onToggleFavorite = { onToggleFavorite(p) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search name, brand, tag or barcode") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, contentDescription = "Clear search") }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("search_field"),
    )
}

@Composable
private fun FilterRow(state: LibraryUiState, onFilterChange: (LibraryFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = state.filter == LibraryFilter.All,
                onClick = { onFilterChange(LibraryFilter.All) },
                label = { Text("All") },
            )
        }
        item {
            FilterChip(
                selected = state.filter == LibraryFilter.Favorites,
                onClick = { onFilterChange(LibraryFilter.Favorites) },
                label = { Text("Favorites") },
                leadingIcon = { Icon(Icons.Outlined.Star, null, Modifier.size(18.dp)) },
            )
        }
        items(state.tags, key = { "tag-$it" }) { tag ->
            val selected = (state.filter as? LibraryFilter.Tag)?.tag.equals(tag, ignoreCase = true)
            FilterChip(
                selected = selected,
                onClick = { onFilterChange(if (selected) LibraryFilter.All else LibraryFilter.Tag(tag)) },
                label = { Text(tag) },
            )
        }
    }
}

@Composable
private fun RecentCard(product: Product, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.width(168.dp)) {
        Column(Modifier.padding(14.dp)) {
            IconBadge(
                icon = product.preferredMethod?.type?.icon ?: Icons.Outlined.Restaurant,
                size = 36.dp,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(10.dp))
            Text(product.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                product.preferredMethod.summary(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ProductRow(
    product: Product,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(product.preferredMethod?.type?.icon ?: Icons.Outlined.Restaurant)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val secondary = listOfNotNull(product.brand, product.preferredMethod.summary()).joinToString(" · ")
                Text(
                    secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (product.favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (product.favorite) "Remove from favorites" else "Add to favorites",
                    tint = if (product.favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun SyncStatusIcon(indicator: SyncIndicator, error: String?) {
    if (indicator == SyncIndicator.LOCAL_ONLY) return
    val (icon, label) = when (indicator) {
        SyncIndicator.SYNCED -> Icons.Outlined.CloudDone to "All changes synced"
        SyncIndicator.PENDING -> Icons.Outlined.CloudUpload to "Changes waiting to sync"
        SyncIndicator.SYNCING -> Icons.Outlined.CloudSync to "Syncing…"
        SyncIndicator.ERROR -> Icons.Outlined.ErrorOutline to "Sync failed: ${error ?: "unknown error"}"
        SyncIndicator.LOCAL_ONLY -> Icons.Outlined.CloudOff to "Local only"
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (indicator == SyncIndicator.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
