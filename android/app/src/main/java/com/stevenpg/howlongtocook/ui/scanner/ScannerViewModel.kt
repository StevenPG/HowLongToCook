package com.stevenpg.howlongtocook.ui.scanner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.stevenpg.howlongtocook.data.model.Product
import com.stevenpg.howlongtocook.data.repository.ProductRepository
import com.stevenpg.howlongtocook.ui.navigation.ScannerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScanOutcome {
    data class ReturnToEditor(val code: String) : ScanOutcome
    data class OpenProduct(val id: String) : ScanOutcome
    data class CreateProduct(val code: String) : ScanOutcome
    data class Choose(val code: String, val products: List<Product>) : ScanOutcome
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProductRepository,
) : ViewModel() {
    val addToEditor: Boolean = savedStateHandle.toRoute<ScannerRoute>().addToEditor

    private val _outcome = MutableStateFlow<ScanOutcome?>(null)
    val outcome: StateFlow<ScanOutcome?> = _outcome.asStateFlow()

    fun onBarcode(raw: String) {
        val code = raw.trim()
        if (code.isEmpty()) return
        if (addToEditor) {
            _outcome.value = ScanOutcome.ReturnToEditor(code)
            return
        }
        viewModelScope.launch {
            val matches = repository.findByBarcode(code)
            _outcome.value = when (matches.size) {
                0 -> ScanOutcome.CreateProduct(code)
                1 -> ScanOutcome.OpenProduct(matches.first().id)
                else -> ScanOutcome.Choose(code, matches)
            }
        }
    }

    fun consumed() {
        _outcome.value = null
    }
}
