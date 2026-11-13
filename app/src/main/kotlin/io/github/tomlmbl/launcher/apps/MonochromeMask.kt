package io.github.tomlmbl.launcher.apps

import kotlin.math.roundToInt

/**
 * Fallback for themed dock icons when an app has no monochrome layer: the icon becomes the opacity of a white
 * mask, then tinted with the theme colors. Pure Kotlin, no Android API, so it can be tested off-device.
 */
object MonochromeMask {

    /** Pixels considered for the luminance range of a non-adaptive icon. */
    private const val MIN_ALPHA = 128
    /** Luminance range below which a non-adaptive icon counts as single-tone: its silhouette becomes the mask. */
    private const val MIN_SPREAD = 0.1f
    /**
     * Share of the height in each top and bottom band, taken as the background. As in `MonochromeIconFactory`:
     * the image is 4/3 of the icon, and the band is half the difference, i.e. 1/8 of the image.
     */
    private const val EDGE_FRACTION = 8
    /**
     * Level below which a generated mask becomes transparent, the rest being stretched back to full: an icon's
     * residual background (5 to 10 % measured on a Pixel) no longer draws a square around the scaled-down glyph.
     */
    private const val FLOOR = 0.12f

    /**
     * Adaptive icon: the algorithm of Launcher3's `MonochromeIconFactory`, rewritten without reusing its code.
     * [pixels] as ARGB, background and foreground layers drawn over the whole image of width [width]; the image is
     * composed over black, opacity is the mean of R, G and B, stretched between its minimum and maximum, then
     * inverted if the top and bottom bands (the presumed background) are light, finally lifted above [FLOOR].
     * Returns a white mask in the same format.
     */
    fun fromIcon(pixels: IntArray, width: Int): IntArray {
        val levels = IntArray(pixels.size) { i -> onBlackMean(pixels[i]) }
        val min = levels.min()
        val max = levels.max()
        if (min < max) {
            val range = (max - min).toFloat()
            val edgeLength = width * (pixels.size / width / EDGE_FRACTION)
            var sum = 0
            for (i in 0 until edgeLength) sum += levels[i] + levels[levels.size - 1 - i]
            val invert = edgeLength > 0 && (sum / (edgeLength * 2f) - min) / range > 0.5f
            for (i in levels.indices) {
                val stretched = (levels[i] - min) / range
                levels[i] = (floor(if (invert) 1f - stretched else stretched) * 255).roundToInt()
            }
        }
        return IntArray(levels.size) { i -> levels[i] shl 24 or 0xFFFFFF }
    }

    /**
     * Non-adaptive icon: luminance becomes opacity. On a mostly dark icon, light parts become opaque; on a mostly
     * light icon (dark glyph on white), the scale is inverted so the background becomes transparent.
     * [pixels] as ARGB; returns a white mask in the same format.
     */
    fun fromLuminance(pixels: IntArray): IntArray {
        var min = 1f
        var max = 0f
        var sum = 0f
        var count = 0
        for (p in pixels) {
            if (p ushr 24 < MIN_ALPHA) continue
            val l = luminance(p)
            if (l < min) min = l
            if (l > max) max = l
            sum += l
            count++
        }
        val spread = max - min
        val invert = count > 0 && (sum / count - min) / spread.coerceAtLeast(MIN_SPREAD) > 0.5f
        return IntArray(pixels.size) { i ->
            val p = pixels[i]
            val alpha = p ushr 24
            // Stretch over the range of the opaque pixels.
            val stretched = ((luminance(p) - min) / spread).coerceIn(0f, 1f)
            val level = when {
                spread < MIN_SPREAD -> 1f
                invert -> floor(1f - stretched)
                else -> floor(stretched)
            }
            (alpha * level).roundToInt() shl 24 or 0xFFFFFF
        }
    }

    /** Level from 0 to 1, lifted above [FLOOR]. */
    private fun floor(level: Float): Float = ((level - FLOOR) / (1f - FLOOR)).coerceIn(0f, 1f)

    /** Mean of R, G and B of the pixel composed over black, from 0 to 255. */
    private fun onBlackMean(p: Int): Int =
        ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) * (p ushr 24) / (3 * 255)

    /** Approximate relative luminance (Rec. 709 coefficients, no linearization). */
    private fun luminance(p: Int): Float =
        (0.2126f * (p shr 16 and 0xFF) + 0.7152f * (p shr 8 and 0xFF) + 0.0722f * (p and 0xFF)) / 255f
}
