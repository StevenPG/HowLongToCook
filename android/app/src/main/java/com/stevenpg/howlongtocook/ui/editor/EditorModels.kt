package com.stevenpg.howlongtocook.ui.editor

import com.stevenpg.howlongtocook.R
import com.stevenpg.howlongtocook.data.model.CookingMethod
import com.stevenpg.howlongtocook.data.model.MethodType
import com.stevenpg.howlongtocook.data.model.Step
import com.stevenpg.howlongtocook.data.model.StepAction
import com.stevenpg.howlongtocook.data.model.Temperature
import com.stevenpg.howlongtocook.data.model.TemperatureUnit
import com.stevenpg.howlongtocook.ui.text.UiText
import com.stevenpg.howlongtocook.ui.text.uiText
import java.util.concurrent.atomic.AtomicLong

private val keys = AtomicLong(1)
internal fun nextKey(): Long = keys.getAndIncrement()

/** Editable step; numbers are kept as text so partially typed input survives recomposition. */
data class StepDraft(
    val key: Long = nextKey(),
    val action: StepAction = StepAction.COOK,
    val minutes: String = "",
    val seconds: String = "",
    val note: String = "",
)

data class MethodDraft(
    val key: Long = nextKey(),
    val type: MethodType,
    val power: String = "",
    val temperature: String = "",
    val unit: TemperatureUnit = TemperatureUnit.F,
    val preheat: Boolean = false,
    val steps: List<StepDraft> = listOf(StepDraft()),
    val notes: String = "",
) {
    val usesTemperature: Boolean get() = type == MethodType.OVEN || type == MethodType.AIR_FRYER
}

fun StepDraft.durationSeconds(): Int? {
    val m = minutes.trim()
    val s = seconds.trim()
    if (m.isEmpty() && s.isEmpty()) return null
    val mins = if (m.isEmpty()) 0 else m.toIntOrNull() ?: return -1
    val secs = if (s.isEmpty()) 0 else s.toIntOrNull() ?: return -1
    if (mins < 0 || secs !in 0..59) return -1
    return mins * 60 + secs
}

fun Step.toDraft(): StepDraft {
    val d = durationSeconds
    return StepDraft(
        action = action,
        minutes = d?.let { (it / 60).toString() } ?: "",
        seconds = d?.let { (it % 60).takeIf { s -> s > 0 }?.toString() ?: "" } ?: "",
        note = note.orEmpty(),
    )
}

fun CookingMethod.toDraft(defaultUnit: TemperatureUnit) = MethodDraft(
    type = type,
    power = power.orEmpty(),
    temperature = temperature?.value?.toString().orEmpty(),
    unit = temperature?.unit ?: defaultUnit,
    preheat = preheat,
    steps = steps.map { it.toDraft() },
    notes = notes.orEmpty(),
)

/** Converts drafts to the domain model, collecting localizable errors. */
fun MethodDraft.toMethod(index: Int, errors: MutableList<UiText>): CookingMethod {
    val number = index + 1
    val temp = if (usesTemperature && temperature.isNotBlank()) {
        val v = temperature.trim().toIntOrNull()
        if (v == null || v !in 0..1000) {
            errors += uiText(R.string.editor_error_temperature, number)
            null
        } else {
            Temperature(v, unit)
        }
    } else {
        null
    }
    val steps = steps.mapIndexedNotNull { i, s ->
        val d = s.durationSeconds()
        if (d == -1) {
            errors += uiText(R.string.editor_error_duration, number, i + 1)
        }
        val blank = s.action == StepAction.COOK && d == null && s.note.isBlank()
        if (blank && steps.size > 1) {
            null // Drop empty trailing "Cook" rows.
        } else {
            Step(action = s.action, durationSeconds = d?.takeIf { it >= 0 }, note = s.note.trim().ifEmpty { null })
        }
    }
    return CookingMethod(
        type = type,
        power = if (usesTemperature) null else power.trim().ifEmpty { null },
        temperature = temp,
        preheat = usesTemperature && preheat,
        steps = steps,
        notes = notes.trim().ifEmpty { null },
    )
}

/** Sensible starting point for each method, matching what most boxes print. */
fun defaultMethod(type: MethodType, unit: TemperatureUnit): MethodDraft = when (type) {
    MethodType.MICROWAVE -> MethodDraft(type = type, power = "High", unit = unit)
    MethodType.OVEN -> MethodDraft(type = type, temperature = if (unit == TemperatureUnit.F) "400" else "200", unit = unit, preheat = true)
    MethodType.AIR_FRYER -> MethodDraft(
        type = type,
        temperature = if (unit == TemperatureUnit.F) "375" else "190",
        unit = unit,
        preheat = true,
        steps = listOf(StepDraft(), StepDraft(action = StepAction.SHAKE), StepDraft()),
    )
    MethodType.STOVETOP -> MethodDraft(type = type, power = "Medium", unit = unit)
}
