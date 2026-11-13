package io.github.tomlmbl.launcher.home

import androidx.compose.animation.core.LinearEasing
import org.junit.Assert.assertEquals
import org.junit.Test

class UnlockArrivalTest {

    private val spec = UnlockArrival(durationMs = 800, startScale = 0.9f, alphaMs = 200, staggerMs = 25, startDelayMs = 50, easing = LinearEasing, pivotY = 0.9f, unlockWaitMs = 500)

    @Test
    fun unlockedDeviceArrives() {
        assertEquals(UnlockGate.Arrive, UnlockGate.of(50, deviceLocked = false, spec))
        assertEquals(UnlockGate.Arrive, UnlockGate.of(900, deviceLocked = false, spec))
    }

    @Test
    fun stillLockedWaitsThenShows() {
        // The device may still report itself locked for a few ms after the keyguard starts leaving.
        assertEquals(UnlockGate.Wait, UnlockGate.of(50, deviceLocked = true, spec))
        assertEquals(UnlockGate.Wait, UnlockGate.of(499, deviceLocked = true, spec))
        // Home behind the keyguard (camera opened over it): shown as is.
        assertEquals(UnlockGate.Show, UnlockGate.of(500, deviceLocked = true, spec))
    }

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
