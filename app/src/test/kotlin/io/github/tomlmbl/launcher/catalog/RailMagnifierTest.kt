package io.github.tomlmbl.launcher.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RailMagnifierTest {

    // Same curve as Tokens.railMagnifier.
    private val magnifier = RailMagnifier(
        scales = floatArrayOf(3f, 2.2f, 1.6f, 1.2f),
        alphas = floatArrayOf(1f, 1f, 1f, 1f, 0.6f, 0.35f),
        farAlpha = 0.15f,
    )

    @Test
    fun curveFollowsTheProposedValues() {
        assertEquals(3f, magnifier.scale(0f), 1e-4f)
        assertEquals(1.6f, magnifier.scale(2f), 1e-4f)
        assertEquals(1f, magnifier.scale(9f), 1e-4f)
        assertEquals(1f, magnifier.alpha(3f), 1e-4f)
        assertEquals(0.6f, magnifier.alpha(4f), 1e-4f)
        assertEquals(0.15f, magnifier.alpha(20f), 1e-4f)
    }

    @Test
    fun curveIsInterpolatedBetweenLetters() {
        assertEquals(2.6f, magnifier.scale(0.5f), 1e-4f)
    }

    @Test
    fun letterUnderTheFingerStaysInPlace() {
        assertEquals(13.5f, magnifier.center(13, finger = 13.5f), 1e-4f)
    }

    @Test
    fun neighboursSpreadSymmetrically() {
        val finger = 13.5f
        val above = finger - magnifier.center(12, finger)
        val below = magnifier.center(14, finger) - finger
        assertEquals(above, below, 1e-4f)
        assertTrue(below > 1f)
    }

    @Test
    fun orderIsKeptAndFarLettersAreShiftedByAConstant() {
        val finger = 13.5f
        val centers = (0 until 27).map { magnifier.center(it, finger) }
        assertEquals(centers.sorted(), centers)
        // Beyond the magnifier, the offset from rest is constant: the surplus of the four enlarged cells.
        assertEquals(magnifier.center(24, finger) - 24.5f, magnifier.center(26, finger) - 26.5f, 1e-4f)
        assertEquals(3f, magnifier.center(26, finger) - 26.5f, 1e-4f)
    }
}
