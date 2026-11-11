package com.stevenpg.howlongtocook.util

import com.stevenpg.howlongtocook.data.model.CookingMethod
import com.stevenpg.howlongtocook.data.model.MethodType
import com.stevenpg.howlongtocook.data.model.Step
import com.stevenpg.howlongtocook.data.model.StepAction
import com.stevenpg.howlongtocook.data.model.Temperature
import com.stevenpg.howlongtocook.data.model.TemperatureUnit
import java.util.Locale
import kotlin.math.roundToInt

/** Countries that conventionally use Fahrenheit for cooking. */
private val fahrenheitRegions = setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW", "FM", "MH", "PR", "GU", "VI", "AS", "MP")

fun defaultUnitFor(locale: Locale): TemperatureUnit =
    if (locale.country.uppercase() in fahrenheitRegions) TemperatureUnit.F else TemperatureUnit.C

/**
 * Converts for display. Same-unit values are shown exactly as printed on the box;
 * converted values snap to conventional oven-dial steps (25°F / 10°C), so 400°F reads
 * as 200°C and 180°C as 350°F — matching what packaging prints in each unit.
 */
fun Temperature.displayIn(unit: TemperatureUnit): String {
    val shown = when {
        this.unit == unit -> value
        unit == TemperatureUnit.C -> roundTo((value - 32) * 5.0 / 9.0, 10)
        else -> roundTo(value * 9.0 / 5.0 + 32, 25)
    }
    return "$shown°${unit.name}"
}

private fun roundTo(v: Double, step: Int): Int = (v / step).roundToInt() * step

/** "7 min", "1:30", "45 sec", "1 hr 15 min". */
fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h == 0 && m == 0 -> "$s sec"
        h == 0 && s == 0 -> "$m min"
        h == 0 -> "%d:%02d".format(m, s)
        m == 0 && s == 0 -> "$h hr"
        else -> "$h hr $m min"
    }
}

/** "4:00" or "1:05:00". */
fun formatClock(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Parses "4", "4:30", "1:04:30" (minutes / m:ss / h:mm:ss). Returns null if invalid. */
fun parseClock(text: String): Int? {
    val t = text.trim()
    if (t.isEmpty()) return null
    val parts = t.split(":")
    if (parts.size > 3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) || it.length > 4 }) return null
    val nums = parts.map { it.toInt() }
    return when (nums.size) {
        1 -> nums[0] * 60
        2 -> if (nums[1] < 60) nums[0] * 60 + nums[1] else null
        else -> if (nums[1] < 60 && nums[2] < 60) nums[0] * 3600 + nums[1] * 60 + nums[2] else null
    }
}

fun StepAction.label(): String = when (this) {
    StepAction.COOK -> "Cook"
    StepAction.STIR -> "Stir"
    StepAction.FLIP -> "Flip"
    StepAction.SHAKE -> "Shake"
    StepAction.STAND -> "Let stand"
    StepAction.VENT -> "Vent"
    StepAction.REMOVE -> "Remove"
    StepAction.OTHER -> "Other"
}

/** "Cook 4:00" / "Stir" — the note is rendered separately. */
fun Step.title(): String = buildString {
    append(if (action == StepAction.OTHER && !note.isNullOrBlank()) note else action.label())
    durationSeconds?.takeIf { it > 0 }?.let { append(" ").append(formatClock(it)) }
}

/** Short summary for list rows: "Microwave · 7 min". */
fun CookingMethod?.summary(): String {
    if (this == null) return "No instructions yet"
    val total = totalSeconds
    return if (total > 0) "${type.label()} · ${formatDuration(total)}" else type.label()
}

fun MethodType.label(): String = when (this) {
    MethodType.MICROWAVE -> "Microwave"
    MethodType.OVEN -> "Oven"
    MethodType.AIR_FRYER -> "Air fryer"
    MethodType.STOVETOP -> "Stovetop"
}
