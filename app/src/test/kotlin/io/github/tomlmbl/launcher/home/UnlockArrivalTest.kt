package io.github.tomlmbl.launcher.home

import androidx.compose.animation.core.LinearEasing
import org.junit.Assert.assertEquals
import org.junit.Test

class UnlockArrivalTest {

    private val spec = UnlockArrival(durationMs = 800, startScale = 0.9f, alphaMs = 200, staggerMs = 25, startDelayMs = 50, easing = LinearEasing, pivotY = 0.9f)

    @Test
    fun elementsStartFromTheBottomOneAfterAnother() {
        assertEquals(0f, spec.progress(0f, 0), 0f)
        assertEquals(0.5f, spec.progress(400f, 0), 1e-6f)
        assertEquals(0f, spec.progress(25f, 1), 0f)
        assertEquals(0.5f, spec.progress(425f, 1), 1e-6f)
        assertEquals(1f, spec.progress(Float.MAX_VALUE, 10), 0f)
    }

    @Test
    fun totalCoversTheLastElement() {
        assertEquals(800 + 10 * 25, spec.totalMs(11))
        assertEquals(1f, spec.progress(spec.totalMs(11).toFloat(), 10), 0f)
        assertEquals(800, spec.totalMs(0))
    }
}
