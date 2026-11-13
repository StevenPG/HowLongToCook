package com.stevenpg.howlongtocook.ui.text

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources

/**
 * Text produced outside the UI (ViewModels, validation, sync) that is resolved against
 * string resources only when displayed, so it follows the current locale. Arguments may
 * themselves be [UiText] and are resolved recursively.
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val value: String) : UiText

    fun resolve(res: Resources): String = when (this) {
        is Raw -> value
        is Res -> res.getString(id, *args.resolved(res))
        is Plural -> res.getQuantityString(id, count, *args.resolved(res))
    }

    private fun List<Any>.resolved(res: Resources): Array<Any> =
        map { if (it is UiText) it.resolve(res) else it }.toTypedArray()
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

fun uiPlural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText = UiText.Plural(id, count, args.toList())

@Composable
fun UiText.asString(): String = resolve(LocalResources.current)
