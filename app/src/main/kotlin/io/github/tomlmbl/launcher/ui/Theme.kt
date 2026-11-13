package io.github.tomlmbl.launcher.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * UI theme: the system's dynamic colors (Material You), light or dark following the system theme. Text over the
 * wallpaper follows the wallpaper's tone, independently of the theme.
 * Dynamic colors are available since API 31, always the case here (minSdk 35).
 */
@Composable
fun LauncherTheme(darkText: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Computed again at each configuration change: a theme switch (uiMode, handled by the activity) recomposes without recreating.
    val dark = isSystemInDarkTheme()
    val scheme = remember(LocalConfiguration.current, dark) { if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalDarkText provides darkText) {
            // Ripple color on touch, matched to the text over the wallpaper.
            CompositionLocalProvider(LocalContentColor provides Tokens.onWallpaper, content = content)
        }
    }
}

/** Content on a theme surface (sheet, menu, bubble): texts in `onSurface` roles. */
@Composable
fun OnSurface(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalOnSurface provides true) {
        CompositionLocalProvider(LocalContentColor provides Tokens.onWallpaper, content = content)
    }
}
