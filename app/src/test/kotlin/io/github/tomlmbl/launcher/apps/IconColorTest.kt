package io.github.tomlmbl.launcher.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconColorTest {

    private val transparent = 0x00000000
    private val white = 0xFFFFFFFF.toInt()
    private val grey = 0xFF808080.toInt()
    private val red = 0xFFE53935.toInt()
    private val blue = 0xFF1E88E5.toInt()
    private val navy = 0xFF0A1E50.toInt()

    private fun icon(vararg parts: Pair<Int, Int>) = parts.flatMap { (color, count) -> List(count) { color } }.toIntArray()

    @Test
    fun singleColorIconKeepsItsColor() {
        assertEquals(red, IconColor.dominant(icon(red to 100)))
    }

    @Test
    fun whiteBackgroundDoesNotHideTheLogo() {
        assertEquals(blue, IconColor.dominant(icon(white to 80, blue to 20)))
    }

    @Test
    fun majorityHueWins() {
        assertEquals(blue, IconColor.dominant(icon(red to 30, blue to 60, transparent to 10)))
    }

    @Test
    fun transparentPixelsAreIgnored() {
        assertEquals(red, IconColor.dominant(icon(transparent to 90, red to 10)))
    }

    @Test
    fun colorlessIconsFallBackToNeutral() {
        assertNull(IconColor.dominant(icon(white to 50, grey to 50)))
        assertNull(IconColor.dominant(icon(transparent to 100)))
        assertNull(IconColor.dominant(IntArray(0)))
    }

    @Test
    fun aFewColoredPixelsAreNotEnough() {
        assertNull(IconColor.dominant(icon(grey to 99, red to 1)))
    }

    /** Raw color: lightening is now handled by the tone set by [DotColor]. */
    @Test
    fun darkColorsAreReturnedUnchanged() {
        assertEquals(navy, IconColor.dominant(icon(navy to 100)))
    }
}
