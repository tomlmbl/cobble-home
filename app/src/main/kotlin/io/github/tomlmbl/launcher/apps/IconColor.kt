package io.github.tomlmbl.launcher.apps

import kotlin.math.roundToInt

/**
 * An app's dot color, derived from its icon. Pure Kotlin, no Android API, so it can be tested off-device.
 * Own computation, no dependency: dominant saturated hue among the opaque pixels of a scaled-down icon.
 * Returns null for an icon with no clear color (gray, black, white or transparent): the UI then shows a
 * neutral dot. Raw color: the displayed tone is set by [DotColor] according to the background.
 */
object IconColor {

    private const val BINS = 24
    private const val MIN_ALPHA = 128
    private const val MIN_SATURATION = 0.3f
    private const val MIN_VALUE = 0.25f
    /** Minimum share of colored pixels among opaque pixels. */
    private const val MIN_SHARE = 0.05f

    /** [pixels] as ARGB, like `Bitmap.getPixels`. */
    fun dominant(pixels: IntArray): Int? {
        val weight = FloatArray(BINS)
        val red = FloatArray(BINS)
        val green = FloatArray(BINS)
        val blue = FloatArray(BINS)
        var opaque = 0
        var colored = 0
        for (p in pixels) {
            if (p ushr 24 < MIN_ALPHA) continue
            opaque++
            val r = p shr 16 and 0xFF
            val g = p shr 8 and 0xFF
            val b = p and 0xFF
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val value = max / 255f
            val saturation = if (max == 0) 0f else (max - min).toFloat() / max
            if (saturation < MIN_SATURATION || value < MIN_VALUE) continue
            colored++
            val bin = (hue(r, g, b, max, min) / (360f / BINS)).toInt() % BINS
            val w = saturation * value
            weight[bin] += w
            red[bin] += r * w
            green[bin] += g * w
            blue[bin] += b * w
        }
        if (opaque == 0 || colored < opaque * MIN_SHARE) return null

        // A hue straddling two bins must not lose to a third one: compare windows of three bins.
        val window = { i: Int -> listOf(i - 1, i, i + 1).map { (it + BINS) % BINS } }
        val best = (0 until BINS).maxBy { i -> window(i).sumOf { weight[it].toDouble() } }
        val bins = window(best)
        val total = bins.sumOf { weight[it].toDouble() }.toFloat()
        val r = bins.sumOf { red[it].toDouble() }.toFloat() / total
        val g = bins.sumOf { green[it].toDouble() }.toFloat() / total
        val b = bins.sumOf { blue[it].toDouble() }.toFloat() / total
        val channel = { v: Float -> v.roundToInt().coerceIn(0, 255) }
        return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    private fun hue(r: Int, g: Int, b: Int, max: Int, min: Int): Float {
        val delta = (max - min).toFloat()
        val h = when (max) {
            r -> 60 * ((g - b) / delta)
            g -> 60 * ((b - r) / delta + 2)
            else -> 60 * ((r - g) / delta + 4)
        }
        return if (h < 0) h + 360 else h
    }
}
