package com.stevenpg.howlongtocook.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stevenpg.howlongtocook.data.model.Product
import com.stevenpg.howlongtocook.data.repository.ProductRepository
import com.stevenpg.howlongtocook.data.settings.SettingsRepository
import com.stevenpg.howlongtocook.data.settings.StorageMode
import com.stevenpg.howlongtocook.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LibraryFilter {
    data object All : LibraryFilter
    data object Favorites : LibraryFilter
    data class Tag(val tag: String) : LibraryFilter
}

enum class SyncIndicator { LOCAL_ONLY, SYNCED, PENDING, SYNCING, ERROR }

data class LibraryUiState(
    val loading: Boolean = true,
    val query: String = "",
    val filter: LibraryFilter = LibraryFilter.All,
    val tags: List<String> = emptyList(),
    val products: List<Product> = emptyList(),
    val recent: List<Product> = emptyList(),
    val totalCount: Int = 0,
    val sync: SyncIndicator = SyncIndicator.LOCAL_ONLY,
    val syncError: String? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: ProductRepository,
    settings: SettingsRepository,
    private val scheduler: SyncScheduler,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<LibraryFilter>(LibraryFilter.All)

    private val syncState = combine(
        settings.settings,
        repository.pendingChanges,
        scheduler.isSyncing,
    ) { s, pending, syncing ->
        val indicator = when {
            s.storageMode == StorageMode.LOCAL -> SyncIndicator.LOCAL_ONLY
            syncing -> SyncIndicator.SYNCING
            s.sync.lastError != null -> SyncIndicator.ERROR
            pending > 0 -> SyncIndicator.PENDING
            else -> SyncIndicator.SYNCED
        }
        indicator to s.sync.lastError
    }

    val state: StateFlow<LibraryUiState> = combine(
        repository.items,
        query,
        filter,
        syncState,
    ) { items, q, f, (indicator, error) ->
        val all = items.map { it.product }
        val tags = all.flatMap { it.tags }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
        val activeFilter = if (f is LibraryFilter.Tag && tags.none { it.equals(f.tag, true) }) LibraryFilter.All else f
        LibraryUiState(
            loading = false,
            query = q,
            filter = activeFilter,
            tags = tags,
            products = all.filter { it.matches(q) && it.matches(activeFilter) },
            recent = items.filter { it.lastViewedAt != null }
                .sortedByDescending { it.lastViewedAt }
                .take(8)
                .map { it.product },
            totalCount = all.size,
            sync = indicator,
            syncError = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setFilter(value: LibraryFilter) {
        filter.value = value
    }

    fun toggleFavorite(product: Product) {
        viewModelScope.launch { repository.setFavorite(product.id, !product.favorite) }
    }

    fun syncNow() = scheduler.syncNow()
}

/** All whitespace-separated terms must match name, brand, a tag or a barcode. */
internal fun Product.matches(query: String): Boolean {
    val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return true
    val haystack = buildList {
        add(name.lowercase())
        brand?.let { add(it.lowercase()) }
        tags.forEach { add(it.lowercase()) }
        barcodes.forEach { add(it.lowercase()) }
    }
    return terms.all { term -> haystack.any { it.contains(term) } }
}

internal fun Product.matches(filter: LibraryFilter): Boolean = when (filter) {
    LibraryFilter.All -> true
    LibraryFilter.Favorites -> favorite
    is LibraryFilter.Tag -> tags.any { it.equals(filter.tag, ignoreCase = true) }
}
