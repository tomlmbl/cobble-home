package io.github.tomlmbl.launcher.home

import androidx.compose.animation.core.LinearEasing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockArrivalTest {

    @Test
    fun unlockToHomeArrives() {
        val watch = UnlockWatch()
        watch.onStop(keyguardLocked = true)
        assertTrue(watch.onStart(deviceLocked = false, keyguardLocked = true))
    }

    @Test
    fun ordinaryReturnDoesNotArrive() {
        val watch = UnlockWatch()
        watch.onStop(keyguardLocked = false)
        assertFalse(watch.onStart(deviceLocked = false, keyguardLocked = false))
    }

    @Test
    fun startBehindTheKeyguardKeepsTheUnlockForLater() {
        // Camera opened over the keyguard, then the Home gesture: Home starts and stops behind it before the unlock.
        val watch = UnlockWatch()
        watch.onStop(keyguardLocked = true)
        assertFalse(watch.onStart(deviceLocked = true, keyguardLocked = true))
        watch.onStop(keyguardLocked = true)
        assertTrue(watch.onStart(deviceLocked = false, keyguardLocked = true))
    }

    @Test
    fun unlockIntoAnAppThenLaterReturnIsOrdinary() {
        val watch = UnlockWatch()
        watch.onStop(keyguardLocked = true)
        // Unlocked straight into an app: back to Home long after, the keyguard is gone.
        assertFalse(watch.onStart(deviceLocked = false, keyguardLocked = false))
        // The pending unlock is consumed.
        watch.onStop(keyguardLocked = false)
        assertFalse(watch.onStart(deviceLocked = false, keyguardLocked = true))
    }

    @Test
    fun arrivalIsConsumedOnce() {
        val watch = UnlockWatch()
        watch.onStop(keyguardLocked = true)
        assertTrue(watch.onStart(deviceLocked = false, keyguardLocked = true))
        watch.onStop(keyguardLocked = false)
        assertFalse(watch.onStart(deviceLocked = false, keyguardLocked = true))
    }

    private val spec = UnlockArrival(durationMs = 800, startScale = 0.9f, alphaMs = 200, staggerMs = 25, startDelayMs = 50, easing = LinearEasing)

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
