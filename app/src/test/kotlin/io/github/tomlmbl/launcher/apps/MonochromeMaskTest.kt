package io.github.tomlmbl.launcher.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonochromeMaskTest {

    private val transparent = 0x00000000
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val darkGrey = 0xFF303030.toInt()
    private val red = 0xFFE53935.toInt()
    private val orange = 0xFFFF8F00.toInt()

    /**
     * 8 × 8 icon: top and bottom bands of one row each (8 / 8), [background] everywhere, [glyph] on the central 4 × 4
     * square, optional [accent] on the first pixel of that square.
     */
    private fun icon(background: Int, glyph: Int, accent: Int? = null): IntArray = IntArray(SIZE * SIZE) { i ->
        val x = i % SIZE
        val y = i / SIZE
        when {
            accent != null && x == 2 && y == 2 -> accent
            x in 2..5 && y in 2..5 -> glyph
            else -> background
        }
    }

    private fun alphas(pixels: IntArray) = MonochromeMask.fromIcon(pixels, SIZE).map { it ushr 24 }

    private fun at(alphas: List<Int>, x: Int, y: Int) = alphas[y * SIZE + x]

    @Test
    fun maskIsWhite() {
        MonochromeMask.fromIcon(icon(red, white), SIZE).forEach { assertEquals(0xFFFFFF, it and 0xFFFFFF) }
    }

    @Test
    fun lightGlyphOnDarkBackgroundKeepsTheGlyph() {
        val a = alphas(icon(darkGrey, white))
        assertEquals(255, at(a, 3, 3))
        assertEquals(0, at(a, 0, 0))
        assertEquals(0, at(a, 0, 3))
    }

    @Test
    fun darkGlyphOnLightBackgroundIsInverted() {
        val a = alphas(icon(white, darkGrey))
        assertEquals(255, at(a, 3, 3))
        assertEquals(0, at(a, 0, 0))
        assertEquals(0, at(a, 7, 7))
    }

    @Test
    fun accentStaysBetweenGlyphAndBackground() {
        // White glyph and orange accent on a colored background. The glyph stays whole.
        val a = alphas(icon(red, white, accent = orange))
        assertEquals(255, at(a, 3, 3))
        assertEquals(0, at(a, 0, 0))
        assertTrue(at(a, 2, 2) in 1..254)
    }

    @Test
    fun faintBackgroundBecomesTransparent() {
        // Case seen on a Pixel: background barely lighter than the minimum, below the threshold; the glyph stays full.
        val faint = 0xFF101010.toInt()
        val pixels = icon(faint, white).also { it[0] = black }
        val a = alphas(pixels)
        assertEquals(0, at(a, 1, 1))
        assertEquals(255, at(a, 3, 3))
    }

    @Test
    fun transparentBackgroundCountsAsBlack() {
        // Foreground only (background layer missing or transparent): composed over black, like a dark background.
        val a = alphas(icon(transparent, white))
        assertEquals(255, at(a, 3, 3))
        assertEquals(0, at(a, 0, 0))
    }

    @Test
    fun darkGlyphOnTransparentBackgroundIsLost() {
        // Limit of the algorithm: a black glyph on a transparent background blends with the black it is composed over.
        assertTrue(alphas(icon(transparent, black)).all { it == 0 })
    }

    @Test
    fun brightBandsDecideTheInversionNotTheMean() {
        // Light background limited to the bands: the pixel mean is dark, but the presumed background is light.
        val pixels = IntArray(SIZE * SIZE) { i -> if (i / SIZE == 0 || i / SIZE == SIZE - 1) white else darkGrey }
        val a = alphas(pixels)
        assertEquals(0, at(a, 0, 0))
        assertEquals(255, at(a, 3, 3))
    }

    @Test
    fun uniformIconKeepsItsMeanAsOpacity() {
        // Without range, neither stretch nor inversion: opacity is the mean of R, G and B.
        val grey = 0xFF808080.toInt()
        assertTrue(alphas(IntArray(SIZE * SIZE) { grey }).all { it == 0x80 })
        assertTrue(alphas(IntArray(SIZE * SIZE) { transparent }).all { it == 0 })
    }

    @Test
    fun translucentPixelsAreComposedOnBlack() {
        // Half-transparent white over black: mid gray, between background and glyph.
        val a = alphas(icon(darkGrey, white, accent = 0x80FFFFFF.toInt()))
        assertTrue(at(a, 2, 2) in 1..254)
        assertEquals(255, at(a, 3, 3))
    }

    // Non-adaptive icons: luminance weighted by opacity, inversion by the mean.

    private fun legacyAlphas(vararg pixels: Int) = MonochromeMask.fromLuminance(pixels).map { it ushr 24 }

    @Test
    fun legacyLightGlyphOnDarkBackgroundKeepsTheGlyph() {
        assertEquals(listOf(255, 0), legacyAlphas(white, black))
    }

    @Test
    fun legacyDarkGlyphOnLightBackgroundKeepsTheGlyph() {
        assertEquals(listOf(0, 0, 0, 255), legacyAlphas(white, white, white, black))
    }

    @Test
    fun legacySingleColorIconKeepsItsSilhouette() {
        assertEquals(listOf(255, 255, 0), legacyAlphas(red, red, transparent))
    }

    @Test
    fun legacyTranslucentPixelsStayTranslucent() {
        assertEquals(listOf(255, 0, 0, 128), legacyAlphas(white, black, black, 0x80FFFFFF.toInt()))
    }

    @Test
    fun legacyFaintBackgroundBecomesTransparent() {
        assertEquals(listOf(255, 0, 0), legacyAlphas(white, black, 0xFF141414.toInt()))
    }

    private companion object {
        const val SIZE = 8
    }
}
