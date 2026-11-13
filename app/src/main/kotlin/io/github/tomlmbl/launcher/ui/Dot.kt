package io.github.tomlmbl.launcher.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.tomlmbl.launcher.apps.App
import io.github.tomlmbl.launcher.apps.DotColor

/** Marks an icon with no clear color; `IconColor` never produces it (zero alpha). */
private const val NEUTRAL = 0

/** Raw colors already received, per app; read and written on the main thread. Corrected at the next computation. */
private val known = HashMap<String, Int>()

/**
 * Dot color of [app], or null until computed: no gray dot that would change hue right away. Only the raw color is
 * cached; the displayed tone follows the accent and the background, hence theme or wallpaper changes without a
 * restart. Without [app], a neutral dot.
 */
@Composable
fun rememberDot(app: App?, color: suspend (App) -> Int?): Color? {
    // Color already received: shown from the first frame, without an empty dot for one frame.
    val raw by produceState(app?.id?.let { known[it] }, app?.id) {
        value = (app?.let { color(it) } ?: NEUTRAL).also { if (app != null) known[app.id] = it }
    }
    val accent = Tokens.accent.toArgb()
    // Light background: a wallpaper with dark text, or the light theme surface.
    val light = if (LocalOnSurface.current) !isSystemInDarkTheme() else LocalDarkText.current
    return remember(raw, accent, light) {
        when (val c = raw) {
            null -> null
            NEUTRAL -> Tokens.dotNeutral
            else -> Color(DotColor.of(c, accent, light))
        }
    }
}
