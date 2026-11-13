package io.github.tomlmbl.launcher.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SheetMotionTest {

    private fun motion() = SheetMotion(CoroutineScope(Dispatchers.Unconfined), 1f, 0f, 0f)

    @Test
    fun unmeasuredSheetIsOffScreen() {
        val m = motion()
        assertEquals(100_000f, m.shown)
    }

    /** A sheet taller than the previous one must not be placed with that one's offset. */
    @Test
    fun nextSheetStartsHiddenAfterAShorterOne() = runBlocking {
        val m = motion()
        m.height = 650f
        m.offset.snapTo(650f) // end of the short sheet's exit
        m.forget()
        assertEquals(100_000f, m.shown)
        m.height = 1100f // first measure of the next sheet, before `show`
        assertEquals(1100f, m.shown)
    }
}
