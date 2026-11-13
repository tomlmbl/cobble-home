package io.github.tomlmbl.launcher.home

import androidx.compose.animation.core.Easing
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp

/**
 * Tells an unlock apart from other returns to Home, without permission, from the keyguard state read at each stop and
 * start. Measured on one Android 16 device: Home is stopped only once the keyguard shows, then started again once the
 * device is unlocked, while the keyguard still reports itself as locked during SystemUI's exit animation (about 400 ms).
 */
class UnlockWatch {
    private var lockedSinceStop = false

    /**
     * [keyguardLocked]: the keyguard covered Home when it stopped. Never cleared here: Home may start and stop again
     * behind the keyguard (camera opened over it).
     */
    fun onStop(keyguardLocked: Boolean) {
        if (keyguardLocked) lockedSinceStop = true
    }

    /**
     * Whether Home arrives from an unlock. Still locked ([deviceLocked]): Home is hidden, nothing is decided.
     * Otherwise the keyguard must still be leaving ([keyguardLocked]): unlocking straight into an app and coming back
     * later is an ordinary return.
     */
    fun onStart(deviceLocked: Boolean, keyguardLocked: Boolean): Boolean {
        if (deviceLocked) return false
        val arrive = lockedSinceStop && keyguardLocked
        lockedSinceStop = false
        return arrive
    }

    /** Pending unlock, for the log. */
    val pending: Boolean get() = lockedSinceStop
}

/**
 * Arrival of Home's elements one by one after an unlock, ranked from the bottom (0) upward; each one fades in over
 * [alphaMs] and grows from [startScale] along [easing] over [durationMs], [staggerMs] after the one below it.
 * The sequence starts [startDelayMs] after Home starts, when SystemUI begins showing it.
 */
class UnlockArrival(
    val durationMs: Int,
    val startScale: Float,
    val alphaMs: Int,
    val staggerMs: Int,
    val startDelayMs: Long,
    val easing: Easing,
) {
    /** Progress (0 hidden, 1 in place) of the element of [rank], [elapsedMs] after the sequence starts. */
    fun progress(elapsedMs: Float, rank: Int): Float =
        ((elapsedMs - rank * staggerMs) / durationMs).coerceIn(0f, 1f)

    /** Length of the sequence for [count] elements. */
    fun totalMs(count: Int): Int = (count - 1).coerceAtLeast(0) * staggerMs + durationMs
}

/**
 * Opacity and scale of one element from its arrival [progress]. The scale is applied at draw time only: layout, hence
 * the bounds given to the gesture return, stays the final one.
 */
internal fun Modifier.arrive(spec: UnlockArrival, progress: () -> Float): Modifier = this
    .graphicsLayer { alpha = (progress() * spec.durationMs / spec.alphaMs).coerceIn(0f, 1f) }
    .drawWithContent {
        val p = progress()
        if (p >= 1f) drawContent() else scale(lerp(spec.startScale, 1f, spec.easing.transform(p))) { this@drawWithContent.drawContent() }
    }
