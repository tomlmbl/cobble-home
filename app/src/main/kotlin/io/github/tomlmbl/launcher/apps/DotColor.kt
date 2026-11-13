package io.github.tomlmbl.launcher.apps

import io.github.tomlmbl.launcher.mcu.blend.Blend
import io.github.tomlmbl.launcher.mcu.hct.Hct

/**
 * Displayed dot color, from the icon's dominant color ([IconColor]): hue harmonized toward the Material You
 * accent, chroma capped, tone set by the background. Uses the Material color utilities vendored in `mcu/`.
 * Pure Kotlin, tested off-device.
 */
object DotColor {

    const val MAX_CHROMA = 40.0
    const val TONE_ON_DARK = 80.0
    const val TONE_ON_LIGHT = 50.0

    /** [icon] and [accent] as ARGB; [lightBackground]: the dot sits on a light background. */
    fun of(icon: Int, accent: Int, lightBackground: Boolean): Int {
        val hct = Hct.fromInt(Blend.harmonize(icon, accent))
        return Hct.from(hct.hue, minOf(hct.chroma, MAX_CHROMA), if (lightBackground) TONE_ON_LIGHT else TONE_ON_DARK).toInt()
    }
}
