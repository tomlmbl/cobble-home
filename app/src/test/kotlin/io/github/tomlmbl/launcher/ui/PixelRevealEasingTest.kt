package io.github.tomlmbl.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelRevealEasingTest {

    @Test
    fun matchesTheLauncher3Path() {
        // Values read from ScalingWorkspaceRevealAnim's original path (fine sampling of both cubics).
        val reference = mapOf(0.05f to 0.0826f, 0.1f to 0.2234f, 0.3f to 0.7832f, 0.5f to 0.9262f, 0.8f to 0.9916f)
        for ((t, y) in reference) assertEquals("t=$t", y, pixelRevealEasing.transform(t), 0.002f)
    }

    @Test
    fun gestureEasingSitsBetweenPixelAndEmphasizedDecelerate() {
        val reference = mapOf(0.05f to 0.27f, 0.1f to 0.42f, 0.3f to 0.83f, 0.5f to 0.94f)
        for ((t, y) in reference) assertEquals("t=$t", y, gestureRevealEasing.transform(t), 0.01f)
        assertEquals(1f, gestureRevealEasing.transform(1f), 1e-4f)
    }

    @Test
    fun endsAndJoinAreContinuous() {
        assertEquals(0f, pixelRevealEasing.transform(0f), 1e-4f)
        assertEquals(1f, pixelRevealEasing.transform(1f), 1e-4f)
        assertEquals(pixelRevealEasing.transform(0.1499f), pixelRevealEasing.transform(0.1501f), 0.002f)
    }

    @Test
    fun staysMonotone() {
        var previous = 0f
        for (i in 1..1000) {
            val y = pixelRevealEasing.transform(i / 1000f)
            assertTrue("i=$i", y >= previous - 1e-5f)
            previous = y
        }
    }
}
