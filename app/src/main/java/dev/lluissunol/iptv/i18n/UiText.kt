package dev.lluissunol.iptv.i18n

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * User-facing text that is not resolved yet: a resource id plus positional
 * format arguments. The data and player layers hold no `Context`, so they
 * return this and the UI resolves it against the current configuration. That
 * also keeps a message correct if the language changes while it is on screen.
 *
 * Arguments must never carry a URL or credential (see `redactUrl()`).
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    fun resolve(context: Context): String = when (this) {
        is Res -> context.getString(id, *args.toTypedArray())
    }

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
    }
}

@Composable
fun UiText.asString(): String = resolve(LocalContext.current)
