package io.github.tomlmbl.launcher.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/**
 * Colored weather picture of the panel. In the light theme, a very light drop shadow under the picture's shape: the
 * white clouds disappeared on the light sheet. The shadow is the picture itself, tinted black, blurred and offset
 * downward; blur as a standard deviation, half the design radius (`Tokens.weatherShadow*`).
 */
@Composable
fun WeatherPicture(@DrawableRes id: Int, size: Dp, modifier: Modifier = Modifier) {
    val painter = painterResource(id)
    Box(modifier.size(size)) {
        if (!isSystemInDarkTheme()) {
            val large = size > Tokens.hourPicture
            val blur = with(LocalDensity.current) { (if (large) Tokens.weatherShadowBlurLarge else Tokens.weatherShadowBlur).toPx() / 2 }
            Image(
                painter, contentDescription = null,
                modifier = Modifier
                    .size(size)
                    .offset(y = if (large) Tokens.weatherShadowOffsetLarge else Tokens.weatherShadowOffset)
                    .graphicsLayer { renderEffect = BlurEffect(blur, blur, TileMode.Decal) },
                colorFilter = ColorFilter.tint(Tokens.weatherShadow),
            )
        }
        Image(painter, contentDescription = null, modifier = Modifier.size(size))
    }
}
