package io.github.tomlmbl.launcher.settings

import io.github.tomlmbl.launcher.context.Dock
import io.github.tomlmbl.launcher.context.DockApp
import io.github.tomlmbl.launcher.context.Profile.HOME
import io.github.tomlmbl.launcher.context.Profile.OUT
import io.github.tomlmbl.launcher.context.Profile.WORK
import io.github.tomlmbl.launcher.context.Profiles
import io.github.tomlmbl.launcher.home.Favorite
import io.github.tomlmbl.launcher.home.Favorites
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigChangeTest {

    private fun fav(id: String) = Favorite(id, "Label $id")
    private fun app(id: String) = DockApp(id, "Label $id")
    private fun favorites(vararg ids: String) = Config(favorites = Favorites(ids.map(::fav)))
    private val docked = Config(
        profiles = Profiles(listOf(WORK, HOME, OUT), WORK, mapOf(WORK to Dock(listOf(app("a"), app("b"), null)))),
    )

    @Test
    fun noChangeGivesNothing() {
        assertNull(ConfigChange.describe(favorites("a", "b"), favorites("a", "b")))
        assertNull(ConfigChange.describe(docked, docked.copy(onboarded = true)))
    }

    @Test
    fun reorderGivesOldPositions() {
        val before = favorites("a", "b", "c")
        assertEquals("fav 3->3 order=2,0,1 removed=0", ConfigChange.describe(before, before.copy(favorites = before.favorites.move(2, 0))))
    }

    @Test
    fun removalAndAdditionAreCounted() {
        assertEquals("fav 3->2 order=0,2 removed=1", ConfigChange.describe(favorites("a", "b", "c"), favorites("a", "c")))
        assertEquals("fav 2->3 order=0,1,+ removed=0", ConfigChange.describe(favorites("a", "b"), favorites("a", "b", "c")))
    }

    @Test
    fun dockSlotsAreDescribed() {
        assertEquals("dock WORK 1*", ConfigChange.describe(docked, docked.setDockSlot(WORK, 1, app("c"))))
        assertEquals("dock WORK 0-", ConfigChange.describe(docked, docked.clearDockSlot(WORK, 0)))
        assertEquals("dock WORK 2+", ConfigChange.describe(docked, docked.setDockSlot(WORK, 2, app("c"))))
        val swapped = docked.copy(profiles = docked.profiles.withDock(WORK, Dock(listOf(app("b"), app("a"), null))))
        assertEquals("dock WORK 0<1,1<0", ConfigChange.describe(docked, swapped))
    }

    @Test
    fun neverWritesIdentifiersOrLabels() {
        val before = Config(
            favorites = Favorites(listOf(fav("0|com.example.secret/.Main"))),
            profiles = docked.profiles,
        )
        val after = before.copy(favorites = Favorites(emptyList())).setDockSlot(WORK, 1, app("0|com.example.other/.Main"))
        val text = ConfigChange.describe(before, after)!! + ConfigChange.summary(after)
        assertFalse(text.contains("example"))
        assertFalse(text.contains("Label"))
    }

    @Test
    fun summaryCountsFavoritesAndOccupiedSlots() {
        assertEquals("fav=0 docks=WORK:2", ConfigChange.summary(docked))
    }
}
