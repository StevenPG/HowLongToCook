package com.stevenpg.howlongtocook.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.stevenpg.howlongtocook.R
import com.stevenpg.howlongtocook.data.model.MethodType
import com.stevenpg.howlongtocook.data.model.Product
import com.stevenpg.howlongtocook.data.model.ProductValidation
import com.stevenpg.howlongtocook.data.model.TemperatureUnit
import com.stevenpg.howlongtocook.data.remote.openfoodfacts.OpenFoodFactsClient
import com.stevenpg.howlongtocook.data.repository.ProductRepository
import com.stevenpg.howlongtocook.data.repository.ValidationException
import com.stevenpg.howlongtocook.data.settings.SettingsRepository
import com.stevenpg.howlongtocook.data.settings.UnitPreference
import com.stevenpg.howlongtocook.ui.navigation.EditorRoute
import com.stevenpg.howlongtocook.ui.text.UiText
import com.stevenpg.howlongtocook.ui.text.toUiText
import com.stevenpg.howlongtocook.ui.text.uiText
import com.stevenpg.howlongtocook.util.defaultUnitFor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val base: Product? = null,
    val name: String = "",
    val brand: String = "",
    val barcodes: List<String> = emptyList(),
    val barcodeInput: String = "",
    val barcodeWarning: UiText? = null,
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val rating: Int? = null,
    val favorite: Boolean = false,
    val notes: String = "",
    val methods: List<MethodDraft> = emptyList(),
    val defaultUnit: TemperatureUnit = TemperatureUnit.F,
    val lookingUp: Boolean = false,
    val filledFromLookup: Boolean = false,
    val errors: List<UiText> = emptyList(),
    val nameError: Boolean = false,
    val saving: Boolean = false,
    val savedId: String? = null,
    val changed: Boolean = false,
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProductRepository,
    private val settings: SettingsRepository,
    private val openFoodFacts: OpenFoodFactsClient,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<EditorRoute>()
    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    /**
     * Completes once the product (or a new blank one) is in [state]. Changes requested
     * before then, such as a barcode returned by the scanner while loading, are queued
     * behind it instead of being overwritten by the loaded state.
     */
    private val loaded = CompletableDeferred<Unit>()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val prefs = settings.current()
        val unit = when (prefs.unit) {
            UnitPreference.FAHRENHEIT -> TemperatureUnit.F
            UnitPreference.CELSIUS -> TemperatureUnit.C
            UnitPreference.AUTO -> defaultUnitFor(Locale.getDefault())
        }
        val existing = route.id?.let { repository.get(it) }
        if (existing != null) {
            _state.value = EditorUiState(
                loading = false,
                isNew = false,
                base = existing,
                name = existing.name,
                brand = existing.brand.orEmpty(),
                barcodes = existing.barcodes,
                tags = existing.tags,
                rating = existing.rating,
                favorite = existing.favorite,
                notes = existing.notes.orEmpty(),
                methods = existing.methods.map { it.toDraft(unit) },
                defaultUnit = unit,
            )
            loaded.complete(Unit)
            return
        }
        val barcode = route.barcode?.trim()?.takeIf { it.isNotEmpty() }
        _state.value = EditorUiState(
            loading = false,
            isNew = true,
            base = repository.newProduct(barcode = barcode),
            barcodes = listOfNotNull(barcode),
            methods = listOf(defaultMethod(MethodType.MICROWAVE, unit)),
            defaultUnit = unit,
            lookingUp = barcode != null && prefs.openFoodFacts,
        )
        loaded.complete(Unit)
        if (barcode != null && prefs.openFoodFacts) lookup(barcode)
    }

    private suspend fun lookup(barcode: String) {
        val suggestion = openFoodFacts.lookup(barcode)
        _state.update { s ->
            if (suggestion == null) {
                s.copy(lookingUp = false)
            } else {
                s.copy(
                    lookingUp = false,
                    name = s.name.ifBlank { suggestion.name.orEmpty() },
                    brand = s.brand.ifBlank { suggestion.brand.orEmpty() },
                    filledFromLookup = true,
                )
            }
        }
    }

    /** Runs [action] now if loaded, otherwise right after loading, preserving call order. */
    private fun afterLoad(action: () -> Unit) {
        if (loaded.isCompleted) {
            action()
        } else {
            viewModelScope.launch {
                loaded.await()
                action()
            }
        }
    }

    private fun edit(f: (EditorUiState) -> EditorUiState) = afterLoad {
        _state.update { f(it).copy(changed = true, errors = emptyList()) }
    }

    private fun set(f: (EditorUiState) -> EditorUiState) = afterLoad { _state.update(f) }

    fun setName(v: String) = edit { it.copy(name = v, nameError = false) }
    fun setBrand(v: String) = edit { it.copy(brand = v) }
    fun setNotes(v: String) = edit { it.copy(notes = v) }
    fun setRating(v: Int?) = edit { it.copy(rating = v) }
    fun setFavorite(v: Boolean) = edit { it.copy(favorite = v) }
    fun setBarcodeInput(v: String) = set { it.copy(barcodeInput = v.filter { c -> c.isLetterOrDigit() || c == '-' }) }
    fun setTagInput(v: String) = set { it.copy(tagInput = v) }

    fun addBarcode(raw: String? = null) = afterLoad {
        val code = (raw ?: state.value.barcodeInput).trim()
        if (code.isEmpty()) return@afterLoad
        if (!ProductValidation.isValidBarcode(code)) {
            _state.update { it.copy(barcodeWarning = uiText(R.string.editor_barcode_invalid)) }
            return@afterLoad
        }
        _state.update { s ->
            s.copy(
                barcodes = (s.barcodes + code).distinct(),
                barcodeInput = if (raw == null) "" else s.barcodeInput,
                barcodeWarning = null,
                changed = true,
                errors = emptyList(),
            )
        }
        viewModelScope.launch {
            val others = repository.findByBarcode(code).filter { it.id != state.value.base?.id }
            if (others.isNotEmpty()) {
                _state.update { it.copy(barcodeWarning = uiText(R.string.editor_barcode_shared, code, others.first().name)) }
            }
        }
    }

    fun removeBarcode(code: String) = edit { s -> s.copy(barcodes = s.barcodes - code, barcodeWarning = null) }

    fun addTag(raw: String? = null) = afterLoad {
        val tags = (raw ?: state.value.tagInput).split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (tags.isEmpty()) return@afterLoad
        _state.update { s ->
            s.copy(
                tags = (s.tags + tags).distinctBy { it.lowercase() },
                tagInput = if (raw == null) "" else s.tagInput,
                changed = true,
                errors = emptyList(),
            )
        }
    }

    fun removeTag(tag: String) = edit { s -> s.copy(tags = s.tags - tag) }

    fun addMethod(type: MethodType) = edit { s -> s.copy(methods = s.methods + defaultMethod(type, s.defaultUnit)) }

    fun removeMethod(key: Long) = edit { s -> s.copy(methods = s.methods.filterNot { it.key == key }) }

    fun moveMethod(key: Long, up: Boolean) = edit { s -> s.copy(methods = s.methods.move(key, up) { it.key }) }

    fun updateMethod(key: Long, f: (MethodDraft) -> MethodDraft) =
        edit { s -> s.copy(methods = s.methods.map { if (it.key == key) f(it) else it }) }

    fun addStep(methodKey: Long) = updateMethod(methodKey) { it.copy(steps = it.steps + StepDraft()) }

    fun removeStep(methodKey: Long, stepKey: Long) =
        updateMethod(methodKey) { m -> m.copy(steps = m.steps.filterNot { it.key == stepKey }) }

    fun moveStep(methodKey: Long, stepKey: Long, up: Boolean) =
        updateMethod(methodKey) { m -> m.copy(steps = m.steps.move(stepKey, up) { it.key }) }

    fun updateStep(methodKey: Long, stepKey: Long, f: (StepDraft) -> StepDraft) =
        updateMethod(methodKey) { m -> m.copy(steps = m.steps.map { if (it.key == stepKey) f(it) else it }) }

    fun save() = afterLoad {
        // Commit anything still typed in the chip inputs.
        if (state.value.barcodeInput.isNotBlank()) addBarcode()
        if (state.value.tagInput.isNotBlank()) addTag()
        val cur = state.value
        val base = cur.base ?: return@afterLoad
        val errors = mutableListOf<UiText>()
        val methods = cur.methods.mapIndexed { i, m -> m.toMethod(i, errors) }
        if (cur.name.isBlank()) errors.add(0, uiText(R.string.editor_error_name))
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors, nameError = cur.name.isBlank()) }
            return@afterLoad
        }
        val product = base.copy(
            name = cur.name,
            brand = cur.brand,
            barcodes = cur.barcodes,
            tags = cur.tags,
            rating = cur.rating,
            favorite = cur.favorite,
            notes = cur.notes,
            methods = methods,
        )
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val saved = repository.save(product)
                _state.update { it.copy(saving = false, savedId = saved.id, changed = false) }
            } catch (e: ValidationException) {
                _state.update { it.copy(saving = false, errors = e.issues.map { issue -> issue.toUiText() }) }
            }
        }
    }
}

private inline fun <T> List<T>.move(key: Long, up: Boolean, keyOf: (T) -> Long): List<T> {
    val i = indexOfFirst { keyOf(it) == key }
    val j = if (up) i - 1 else i + 1
    if (i < 0 || j !in indices) return this
    return toMutableList().apply { add(j, removeAt(i)) }
}
