package io.github.tomlmbl.launcher.home

import android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT
import android.app.WallpaperColors.HINT_SUPPORTS_DARK_THEME
import io.github.tomlmbl.launcher.home.WallpaperTone.Companion.darkText
import io.github.tomlmbl.launcher.settings.TextOnWallpaper.AUTOMATIC
import io.github.tomlmbl.launcher.settings.TextOnWallpaper.DARK
import io.github.tomlmbl.launcher.settings.TextOnWallpaper.LIGHT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Color of text over the wallpaper: the Automatic rule, and a manual choice overriding it. */
class WallpaperToneTest {

    /** Dark text hint only, dark theme hint only, no hint (as on a real wallpaper), null colors. */
    private val cases = listOf(HINT_SUPPORTS_DARK_TEXT, HINT_SUPPORTS_DARK_THEME, 0, null)

    @Test
    fun automaticFollowsRuleM() {
        assertTrue(darkText(AUTOMATIC, HINT_SUPPORTS_DARK_TEXT))
        assertFalse(darkText(AUTOMATIC, HINT_SUPPORTS_DARK_THEME))
        assertTrue(darkText(AUTOMATIC, 0))
        assertFalse(darkText(AUTOMATIC, null))
    }

    @Test
    fun hintComputedFromTheImageDoesNotChangeTheRule() {
        // Bit 4: hints computed from the image (`HINT_FROM_BITMAP`, hidden), present along with the others.
        assertTrue(darkText(AUTOMATIC, 4))
        assertFalse(darkText(AUTOMATIC, 4 or HINT_SUPPORTS_DARK_THEME))
    }

    @Test
    fun manualChoiceWinsOverEveryHint() {
        assertEquals(List(cases.size) { false }, cases.map { darkText(LIGHT, it) })
        assertEquals(List(cases.size) { true }, cases.map { darkText(DARK, it) })
    }
}
