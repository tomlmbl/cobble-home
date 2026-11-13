package io.github.tomlmbl.launcher.apps

import io.github.tomlmbl.launcher.mcu.blend.Blend
import io.github.tomlmbl.launcher.mcu.hct.Hct
import io.github.tomlmbl.launcher.mcu.utils.MathUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DotColorTest {

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val green = 0xFF00FF00.toInt()
    private val navy = 0xFF0A1E50.toInt()
    private val orange = 0xFFFF9800.toInt()
    private val samples = listOf(red, blue, green, navy, orange, 0xFFE53935.toInt(), 0xFF7CB342.toInt(), 0xFF5D4037.toInt())
    private val accents = listOf(blue, 0xFF6750A4.toInt(), 0xFF8BC34A.toInt())

    /** Reference values from the upstream tests (`dart/test/blend_test.dart`, same commit): checks the vendored copy. */
    @Test
    fun copiedEngineMatchesUpstreamReferences() {
        assertEquals(0xFFFB0057.toInt(), Blend.harmonize(red, blue))
        assertEquals(0xFF5700DC.toInt(), Blend.harmonize(blue, red))
        assertEquals(0xFF00FC94.toInt(), Blend.harmonize(green, blue))
    }

    @Test
    fun toneFollowsTheBackground() {
        for (icon in samples) for (accent in accents) {
            assertEquals(DotColor.TONE_ON_DARK, Hct.fromInt(DotColor.of(icon, accent, lightBackground = false)).tone, 1.0)
            assertEquals(DotColor.TONE_ON_LIGHT, Hct.fromInt(DotColor.of(icon, accent, lightBackground = true)).tone, 1.0)
        }
    }

    @Test
    fun chromaIsCappedNotForced() {
        for (icon in samples) for (accent in accents) for (light in listOf(false, true)) {
            assertTrue(Hct.fromInt(DotColor.of(icon, accent, light)).chroma <= DotColor.MAX_CHROMA + 1)
        }
        // A low-saturation color stays low-saturation.
        val muted = 0xFF8D8478.toInt()
        assertTrue(Hct.fromInt(DotColor.of(muted, blue, false)).chroma <= Hct.fromInt(muted).chroma + 1)
    }

    @Test
    fun hueMovesAtMost15DegreesTowardTheAccent() {
        for (icon in samples) for (accent in accents) {
            val from = Hct.fromInt(icon).hue
            val to = Hct.fromInt(accent).hue
            val out = Hct.fromInt(DotColor.of(icon, accent, false)).hue
            // 2° tolerance: hue read back from a color rounded to 8-bit RGB, imprecise at low chroma (brown: 1.1°).
            assertTrue(MathUtils.differenceDegrees(from, out) <= 15 + 2)
            assertTrue(MathUtils.differenceDegrees(out, to) <= MathUtils.differenceDegrees(from, to) + 2)
        }
    }

    /** A midnight blue becomes legible on a dark background and keeps its hue. */
    @Test
    fun navyIsLiftedOnDarkBackground() {
        val out = Hct.fromInt(DotColor.of(navy, navy, false))
        assertEquals(80.0, out.tone, 1.0)
        assertTrue(MathUtils.differenceDegrees(out.hue, Hct.fromInt(navy).hue) <= 2)
    }
}
