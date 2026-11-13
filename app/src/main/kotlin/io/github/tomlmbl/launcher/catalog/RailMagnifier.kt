package io.github.tomlmbl.launcher.catalog

import kotlin.math.abs
import kotlin.math.floor

/**
 * Rail magnifier. Pure Kotlin, no Android API, so it can be tested off-device.
 * Positions are in rest-rail cells: letter i spans [i, i + 1], its center is i + 0.5.
 * The finger is always located on the rail at rest; magnification only stretches the display around it,
 * so the point under the finger does not move.
 *
 * [scales] and [alphas]: values at 0, 1, 2… cells from the finger, interpolated between points; beyond, 1 and [farAlpha].
 */
class RailMagnifier(
    private val scales: FloatArray,
    private val alphas: FloatArray,
    private val farAlpha: Float,
) {

    fun scale(distance: Float): Float = curve(scales, 1f, abs(distance))

    fun alpha(distance: Float): Float = curve(alphas, farAlpha, abs(distance))

    /** Displayed center of letter [index], with the finger at position [finger] of the rest rail. */
    fun center(index: Int, finger: Float): Float {
        val rest = index + 0.5f
        val distance = rest - finger
        return finger + Math.signum(distance) * stretch(abs(distance))
    }

    /** Displayed distance for a rest distance [d]: integral of the scale between the finger and [d]. */
    private fun stretch(d: Float): Float {
        var total = 0f
        var from = 0f
        while (from < d) {
            val to = minOf(floor(from) + 1f, d)
            total += (scale(from) + scale(to)) / 2f * (to - from)
            from = to
        }
        return total
    }

    private fun curve(points: FloatArray, beyond: Float, d: Float): Float {
        val i = floor(d).toInt()
        val a = points.getOrElse(i) { beyond }
        val b = points.getOrElse(i + 1) { beyond }
        return a + (b - a) * (d - i)
    }
}
