package com.stevenpg.howlongtocook.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Canonical product representation (docs/DESIGN.md §5). This exact JSON shape is used for
 * S3/R2 `library.json`, the self-hosted server API and local export files.
 */
@Serializable
data class Product(
    val id: String,
    val name: String,
    val brand: String? = null,
    val barcodes: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false,
    val rating: Int? = null,
    val notes: String? = null,
    val methods: List<CookingMethod> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
) {
    /** The first method is the preferred one. */
    val preferredMethod: CookingMethod? get() = methods.firstOrNull()
}

@Serializable
enum class MethodType {
    @SerialName("MICROWAVE") MICROWAVE,
    @SerialName("OVEN") OVEN,
    @SerialName("AIR_FRYER") AIR_FRYER,
    @SerialName("STOVETOP") STOVETOP,
}

@Serializable
data class CookingMethod(
    val type: MethodType,
    val power: String? = null,
    val temperature: Temperature? = null,
    val preheat: Boolean = false,
    val steps: List<Step> = emptyList(),
    val notes: String? = null,
) {
    val totalSeconds: Int get() = steps.sumOf { it.durationSeconds ?: 0 }
}

@Serializable
enum class TemperatureUnit { F, C }

@Serializable
data class Temperature(val value: Int, val unit: TemperatureUnit)

@Serializable
enum class StepAction { COOK, STIR, FLIP, SHAKE, STAND, VENT, REMOVE, OTHER }

@Serializable
data class Step(
    val action: StepAction,
    val durationSeconds: Int? = null,
    val note: String? = null,
)

/** Whole-library document: S3/R2 object body, server export/import and local backup format. */
@Serializable
data class Library(
    val schemaVersion: Int = SCHEMA_VERSION,
    val updatedAt: Long = 0,
    val products: List<Product> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}
