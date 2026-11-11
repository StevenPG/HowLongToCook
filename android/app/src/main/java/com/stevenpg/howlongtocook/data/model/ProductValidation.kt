package com.stevenpg.howlongtocook.data.model

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

    /** Normalizes strings/lists exactly as the server does; returns a list of problems (empty = valid). */
    fun normalize(product: Product): Pair<Product, List<String>> {
        val errors = mutableListOf<String>()
        val name = product.name.trim()
        if (name.isEmpty()) errors += "Name is required"
        if (name.length > MAX_NAME) errors += "Name must be at most $MAX_NAME characters"

        val barcodes = product.barcodes.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        barcodes.filterNot(::isValidBarcode).forEach { errors += "Invalid barcode \"$it\"" }
        if (barcodes.size > MAX_BARCODES) errors += "At most $MAX_BARCODES barcodes"

        val tags = product.tags.map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        if (tags.any { it.length > MAX_TAG }) errors += "Tags must be at most $MAX_TAG characters"
        if (tags.size > MAX_TAGS) errors += "At most $MAX_TAGS tags"

        if (product.rating != null && product.rating !in 1..5) errors += "Rating must be 1-5"
        if (product.methods.size > MAX_METHODS) errors += "At most $MAX_METHODS methods"

        val methods = product.methods.map { m ->
            if (m.steps.size > MAX_STEPS) errors += "At most $MAX_STEPS steps per method"
            m.temperature?.let { if (it.value !in 0..1000) errors += "Temperature out of range" }
            m.copy(
                power = m.power.clean(),
                notes = m.notes.clean(),
                steps = m.steps.map { s ->
                    s.durationSeconds?.let {
                        if (it !in 0..MAX_DURATION_SECONDS) errors += "Step duration out of range"
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
        return normalized to errors
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
