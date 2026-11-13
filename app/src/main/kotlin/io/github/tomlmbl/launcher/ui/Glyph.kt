package io.github.tomlmbl.launcher.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/** Material Symbols icon, decorative, tinted like the text. */
@Composable
fun Glyph(@DrawableRes id: Int, size: Dp, tint: Color) {
    Image(painterResource(id), contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = Modifier.size(size))
}
