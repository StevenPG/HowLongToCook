package com.stevenpg.howlongtocook.data.model

/** A rule violation; mapped to localized text by the UI (see ui/text/Labels.kt). */
data class ValidationIssue(val type: Type, val value: String? = null, val limit: Int? = null) {
    enum class Type {
        NAME_REQUIRED,
        NAME_TOO_LONG,
        INVALID_BARCODE,
        TOO_MANY_BARCODES,
        TAG_TOO_LONG,
        TOO_MANY_TAGS,
        RATING_OUT_OF_RANGE,
        TOO_MANY_METHODS,
        TOO_MANY_STEPS,
        TEMPERATURE_OUT_OF_RANGE,
        DURATION_OUT_OF_RANGE,
    }
}

/** Validation rules shared with the server (server/src/model.rs). */
object ProductValidation {
    const val MAX_NAME = 200
    const val MAX_TEXT = 4000
    const val MAX_BARCODES = 20
    const val MAX_TAGS = 20
    const val MAX_TAG = 40
    const val MAX_METHODS = 10
    const val MAX_STEPS = 50
    const val MAX_DURATION_SECONDS = 86_400
    const val MAX_POWER = 60
    const val MAX_STEP_NOTE = 500

    private val barcodePattern = Regex("^[0-9A-Za-z-]{4,64}$")

    fun isValidBarcode(code: String): Boolean = barcodePattern.matches(code)

    /** Normalizes strings/lists exactly as the server does; returns the problems found (empty = valid). */
    fun normalize(product: Product): Pair<Product, List<ValidationIssue>> {
        val issues = mutableListOf<ValidationIssue>()
        fun add(type: ValidationIssue.Type, value: String? = null, limit: Int? = null) {
            val issue = ValidationIssue(type, value, limit)
            if (issue !in issues) issues += issue
        }

        val name = product.name.trim()
        if (name.isEmpty()) add(ValidationIssue.Type.NAME_REQUIRED)
        if (name.length > MAX_NAME) add(ValidationIssue.Type.NAME_TOO_LONG, limit = MAX_NAME)

        val barcodes = product.barcodes.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        barcodes.filterNot(::isValidBarcode).forEach { add(ValidationIssue.Type.INVALID_BARCODE, value = it) }
        if (barcodes.size > MAX_BARCODES) add(ValidationIssue.Type.TOO_MANY_BARCODES, limit = MAX_BARCODES)

        val tags = product.tags.map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        if (tags.any { it.length > MAX_TAG }) add(ValidationIssue.Type.TAG_TOO_LONG, limit = MAX_TAG)
        if (tags.size > MAX_TAGS) add(ValidationIssue.Type.TOO_MANY_TAGS, limit = MAX_TAGS)

        if (product.rating != null && product.rating !in 1..5) add(ValidationIssue.Type.RATING_OUT_OF_RANGE)
        if (product.methods.size > MAX_METHODS) add(ValidationIssue.Type.TOO_MANY_METHODS, limit = MAX_METHODS)

        val methods = product.methods.map { m ->
            if (m.steps.size > MAX_STEPS) add(ValidationIssue.Type.TOO_MANY_STEPS, limit = MAX_STEPS)
            m.temperature?.let { if (it.value !in 0..1000) add(ValidationIssue.Type.TEMPERATURE_OUT_OF_RANGE) }
            m.copy(
                power = m.power.clean(),
                notes = m.notes.clean(),
                steps = m.steps.map { s ->
                    s.durationSeconds?.let {
                        if (it !in 0..MAX_DURATION_SECONDS) add(ValidationIssue.Type.DURATION_OUT_OF_RANGE)
                    }
                    s.copy(note = s.note.clean())
                },
            )
        }
        val normalized = product.copy(
            name = name,
            brand = product.brand.clean(),
            notes = product.notes.clean(),
            barcodes = barcodes,
            tags = tags,
            methods = methods,
        )
        return normalized to issues
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
