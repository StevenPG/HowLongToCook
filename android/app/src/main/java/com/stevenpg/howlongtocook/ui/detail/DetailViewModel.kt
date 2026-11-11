package com.stevenpg.howlongtocook.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stevenpg.howlongtocook.data.model.Product
import com.stevenpg.howlongtocook.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = true,
    val product: Product? = null,
    val selectedMethod: Int = 0,
    val deleted: Boolean = false,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProductRepository,
) : ViewModel() {
    // Shared by DetailRoute and CookModeRoute, which both carry an `id` argument.
    private val id: String = checkNotNull(savedStateHandle["id"])
    private val selected = MutableStateFlow(0)
    private val deleted = MutableStateFlow(false)

    val state: StateFlow<DetailUiState> = combine(repository.observe(id), selected, deleted) { p, sel, del ->
        DetailUiState(
            loading = false,
            product = p,
            selectedMethod = sel.coerceIn(0, ((p?.methods?.size ?: 1) - 1).coerceAtLeast(0)),
            deleted = del,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    init {
        viewModelScope.launch { repository.markViewed(id) }
    }

    fun selectMethod(index: Int) {
        selected.value = index
    }

    fun toggleFavorite() {
        val p = state.value.product ?: return
        viewModelScope.launch { repository.setFavorite(p.id, !p.favorite) }
    }

    fun delete() {
        viewModelScope.launch {
            repository.delete(id)
            deleted.value = true
        }
    }
}
