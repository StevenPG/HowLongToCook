package com.stevenpg.howlongtocook.util

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.stevenpg.howlongtocook.R
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
 * as 200°C and 180°C as 350°F, matching what packaging prints in each unit.
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

/** "7 min", "1:30", "45 sec", "1 hr 15 min" (localized). */
fun formatDuration(res: Resources, seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h == 0 && m == 0 -> res.getString(R.string.duration_seconds, s)
        h == 0 && s == 0 -> res.getString(R.string.duration_minutes, m)
        h == 0 -> res.getString(R.string.duration_minutes_seconds, m, s)
        m == 0 && s == 0 -> res.getString(R.string.duration_hours, h)
        else -> res.getString(R.string.duration_hours_minutes, h, m)
    }
}

/** "4:00" or "1:05:00". Digits only, so not localized. */
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

@get:StringRes
val StepAction.labelRes: Int
    get() = when (this) {
        StepAction.COOK -> R.string.step_cook
        StepAction.STIR -> R.string.step_stir
        StepAction.FLIP -> R.string.step_flip
        StepAction.SHAKE -> R.string.step_shake
        StepAction.STAND -> R.string.step_stand
        StepAction.VENT -> R.string.step_vent
        StepAction.REMOVE -> R.string.step_remove
        StepAction.OTHER -> R.string.step_other
    }

@get:StringRes
val MethodType.labelRes: Int
    get() = when (this) {
        MethodType.MICROWAVE -> R.string.method_microwave
        MethodType.OVEN -> R.string.method_oven
        MethodType.AIR_FRYER -> R.string.method_air_fryer
        MethodType.STOVETOP -> R.string.method_stovetop
    }

fun StepAction.label(res: Resources): String = res.getString(labelRes)

fun MethodType.label(res: Resources): String = res.getString(labelRes)

/** "Cook 4:00" / "Stir"; an OTHER step uses its note as the title. The note is shown separately otherwise. */
fun Step.title(res: Resources): String {
    val base = if (action == StepAction.OTHER && !note.isNullOrBlank()) note else action.label(res)
    val time = durationSeconds?.takeIf { it > 0 }?.let(::formatClock) ?: return base
    return res.getString(R.string.step_title_with_time, base, time)
}

/** Short summary for list rows: "Microwave · 7 min". */
fun CookingMethod?.summary(res: Resources): String {
    if (this == null) return res.getString(R.string.no_instructions_yet)
    val total = totalSeconds
    return if (total > 0) {
        res.getString(R.string.method_summary, type.label(res), formatDuration(res, total))
    } else {
        type.label(res)
    }
}

// Composable conveniences, read from the current configuration's resources.

@Composable
fun MethodType.label(): String = label(LocalResources.current)

@Composable
fun StepAction.label(): String = label(LocalResources.current)

@Composable
fun Step.title(): String = title(LocalResources.current)

@Composable
fun CookingMethod?.summary(): String = summary(LocalResources.current)

@Composable
fun formatDuration(seconds: Int): String = formatDuration(LocalResources.current, seconds)
