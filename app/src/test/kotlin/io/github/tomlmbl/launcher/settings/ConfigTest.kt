package io.github.tomlmbl.launcher.settings

import io.github.tomlmbl.launcher.context.ContextEngine
import io.github.tomlmbl.launcher.context.ContextState
import io.github.tomlmbl.launcher.context.Dock
import io.github.tomlmbl.launcher.context.DockApp
import io.github.tomlmbl.launcher.context.Profile.HOME
import io.github.tomlmbl.launcher.context.Profile.OUT
import io.github.tomlmbl.launcher.context.Profile.TRAVEL
import io.github.tomlmbl.launcher.context.Profile.WORK
import io.github.tomlmbl.launcher.context.Profiles
import io.github.tomlmbl.launcher.home.Favorite
import io.github.tomlmbl.launcher.home.Favorites
import io.github.tomlmbl.launcher.home.MAX_FAVORITES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigTest {

    private fun app(id: String) = DockApp(id, "Label $id")
    private fun fav(id: String) = Favorite(id, "Label $id")
    private val withProfiles = Config(
        favorites = Favorites(listOf(fav("f"))),
        profiles = Profiles(listOf(WORK, HOME, OUT), WORK, mapOf(WORK to Dock(listOf(app("d"), null, null)))),
    )

    @Test
    fun noContextChangeTouchesTheFavorites() {
        // Invariant: switching, profile choice and dock never change the favorites or their order.
        val config = withProfiles.copy(favorites = Favorites(listOf(fav("a"), fav("b"), fav("c"))))
        val switched = config.copy(profiles = ContextEngine.atSafeMoment(ContextState(config.profiles, HOME)).profiles)
        assertEquals(HOME, switched.profiles.active)
        assertEquals(config.favorites, switched.favorites)
        assertEquals(config.favorites, config.selectProfiles(listOf(WORK, HOME, TRAVEL)).favorites)
        assertEquals(config.favorites, config.selectProfiles(emptyList()).favorites)
        assertEquals(config.favorites, config.withDockApps(HOME, listOf(app("x"), app("y"), app("z"))).favorites)
    }

    @Test
    fun labelsWithTabsOrLineBreaksStayOnTheirLine() {
        // ConfigFormat.clean: a label cannot break the line-based format.
        val config = Config(favorites = Favorites(listOf(Favorite("a", "x\ty\nz"), fav("b"))))
        assertEquals(listOf("a", "b"), ConfigFormat.decode(ConfigFormat.encode(config)).favorites.items.map { it.appId })
        assertEquals("x y z", ConfigFormat.decode(ConfigFormat.encode(config)).favorites.items[0].label)
    }

    @Test
    fun duplicateFavoriteLinesDoNotShortenTheList() {
        // A duplicate in a hand-edited file does not take a favorite's place.
        val lines = listOf("a", "a") + (1 until MAX_FAVORITES).map { "f$it" }
        val text = "launcher-config\t1\n" + lines.joinToString("") { "fav\t$it\t$it\n" }
        assertEquals(listOf("a") + (1 until MAX_FAVORITES).map { "f$it" }, ConfigFormat.decode(text).favorites.items.map { it.appId })
    }

    @Test
    fun appInADockCannotBecomeFavorite() {
        assertEquals(withProfiles, withProfiles.addFavorite(fav("d")))
        assertEquals(withProfiles, withProfiles.replaceFavorite("f", fav("d")))
    }

    @Test
    fun favoriteCannotGoIntoADock() {
        assertEquals(withProfiles, withProfiles.setDockSlot(HOME, 0, app("f")))
        assertEquals(DockAdd.Refused, withProfiles.addToDock(HOME, app("f")))
    }

    @Test
    fun anAppCanBeInTheDocksOfSeveralProfiles() {
        val added = withProfiles.addToDock(HOME, app("d")) as DockAdd.Added
        assertEquals(0, added.index)
        assertTrue("d" in added.config.profiles.dock(HOME))
        assertTrue("d" in added.config.profiles.dock(WORK))
    }

    @Test
    fun addToDockFillsTheFirstFreeSlotOrReportsFull() {
        val added = withProfiles.addToDock(WORK, app("x")) as DockAdd.Added
        assertEquals(1, added.index)
        val full = withProfiles.setDockSlot(WORK, 1, app("y")).setDockSlot(WORK, 2, app("z"))
        assertEquals(DockAdd.Full, full.addToDock(WORK, app("x")))
    }

    @Test
    fun sleepingDockDoesNotBlockFavorites() {
        val asleep = withProfiles.selectProfiles(listOf(TRAVEL, HOME, OUT))
        assertFalse(asleep.inDock("d"))
        val favorite = asleep.addFavorite(fav("d"))
        assertTrue(favorite.isFavorite("d"))
        val back = favorite.selectProfiles(listOf(WORK, HOME, OUT))
        assertEquals(listOf<DockApp?>(null, null, null), back.profiles.dock(WORK).slots)
        assertTrue(back.isFavorite("d"))
    }

    @Test
    fun dockAppStaysInItsDockWhenFavoritesAreFull() {
        val full = withProfiles.copy(favorites = Favorites((1..MAX_FAVORITES).map { fav("f$it") }))
        assertEquals(full, full.moveToFavorites(fav("d")))
        assertTrue(full.inDock("d"))
    }

    @Test
    fun replacingAFavoriteKeepsItsPlaceTakesTheAppOutOfDocksAndUndoes() {
        val full = withProfiles.copy(favorites = Favorites((1..MAX_FAVORITES).map { fav("f$it") }))
        val places = full.dockPlaces("d")
        val replaced = full.replaceFavoriteFromMenu("f3", fav("d"))
        assertEquals(listOf("f1", "f2", "d") + (4..MAX_FAVORITES).map { "f$it" }, replaced.favorites.items.map { it.appId })
        assertFalse(replaced.inDock("d"))
        assertEquals(full, replaced.undo(MenuUndo.Replaced(fav("f3"), "d", places)))
    }

    @Test
    fun dockAppMovesToFavoritesFromEveryDockAndUndoPutsItBack() {
        val twoDocks = (withProfiles.addToDock(HOME, app("d")) as DockAdd.Added).config.setDockSlot(HOME, 2, app("h"))
        val places = twoDocks.dockPlaces("d")
        val moved = twoDocks.moveToFavorites(fav("d"))
        assertTrue(moved.isFavorite("d"))
        assertFalse(moved.inDock("d"))
        assertEquals(listOf("f", "d"), moved.favorites.items.map { it.appId })
        assertEquals(twoDocks, moved.undo(MenuUndo.ToFavorites("d", places)))
    }

    @Test
    fun favoriteMovesToADockAndUndoRestoresItsPosition() {
        val two = withProfiles.copy(favorites = Favorites(listOf(fav("f"), fav("g"))))
        val added = two.moveToDock(WORK, app("f")) as DockAdd.Added
        assertEquals(1, added.index)
        assertFalse(added.config.isFavorite("f"))
        assertEquals(app("f"), added.config.profiles.dock(WORK).slots[1])
        val undo = MenuUndo.ToDock(fav("f"), 0, DockPlace(WORK, 1, app("f")))
        assertEquals(two, added.config.undo(undo))
    }

    @Test
    fun favoriteMovingToAFullDockChangesNothing() {
        val full = withProfiles.setDockSlot(WORK, 1, app("y")).setDockSlot(WORK, 2, app("z"))
        assertEquals(DockAdd.Full, full.moveToDock(WORK, app("f")))
        val replaced = full.replaceInDock(WORK, 0, app("f"))
        assertFalse(replaced.isFavorite("f"))
        assertEquals(app("f"), replaced.profiles.dock(WORK).slots[0])
    }

    @Test
    fun removingFromDockFreesItsSlotOnly() {
        val full = withProfiles.setDockSlot(WORK, 2, app("z"))
        assertEquals(listOf(null, null, app("z")), full.removeFromDock(WORK, "d").profiles.dock(WORK).slots)
    }

    @Test
    fun onboardingChoicesKeepTheirOrderAndRespectTheExclusion() {
        val favorites = withProfiles.withFavorites(listOf(fav("b"), fav("d"), fav("a")))
        assertEquals(listOf("b", "a"), favorites.favorites.items.map { it.appId })
        val dock = withProfiles.withDockApps(HOME, listOf(app("x"), app("f"), app("y")))
        assertEquals(listOf(app("x"), app("y"), null), dock.profiles.dock(HOME).slots)
    }

    @Test
    fun i2FileIsReadWithoutLoss() {
        val i2 = "launcher-config\t1\nfav\t0|a/.A\tAlpha\nfav\t0|b/.B\tBeta\nclock\tSERIF\tLIGHT\n"
        val config = ConfigFormat.decode(i2)
        assertEquals(listOf("0|a/.A", "0|b/.B"), config.favorites.items.map { it.appId })
        assertEquals(ClockFont(ClockFamily.SERIF, ClockWeight.LIGHT), config.clockFont)
        assertFalse(config.profiles.enabled)
        assertFalse(config.onboarded)
        assertEquals(TextOnWallpaper.AUTOMATIC, config.textOnWallpaper)
    }

    @Test
    fun textOnWallpaperRoundTripsAndUnknownValueFallsBackToAutomatic() {
        TextOnWallpaper.entries.forEach { choice ->
            val config = withProfiles.copy(textOnWallpaper = choice)
            assertEquals(config, ConfigFormat.decode(ConfigFormat.encode(config)))
        }
        assertFalse(ConfigFormat.encode(withProfiles).contains("\ntext"))
        assertEquals(TextOnWallpaper.AUTOMATIC, ConfigFormat.decode("launcher-config\t1\ntext\tSEPIA\n").textOnWallpaper)
    }

    @Test
    fun roundTripKeepsSelectionActiveSleepingAndEmptyDocks() {
        val config = withProfiles
            .setDockSlot(WORK, 2, app("z"))
            .copy(onboarded = true)
            .let { it.copy(profiles = it.profiles.withDock(TRAVEL, Dock(listOf(null, app("t"), null))).withDock(HOME, Dock())) }
            .let { it.copy(profiles = it.profiles.copy(active = OUT)) }
        assertEquals(config, ConfigFormat.decode(ConfigFormat.encode(config)))
    }

    @Test
    fun skippedProfilesAndNeverOnboardedAreDistinct() {
        val skipped = Config(onboarded = true)
        val decoded = ConfigFormat.decode(ConfigFormat.encode(skipped))
        assertTrue(decoded.onboarded)
        assertFalse(decoded.profiles.enabled)
    }

    @Test
    fun invalidSelectionIsReadAsNoProfiles() {
        val text = "launcher-config\t1\nprofiles\tWORK\tHOME\nactive\tWORK\n"
        assertFalse(ConfigFormat.decode(text).profiles.enabled)
    }

    @Test
    fun handEditedFileCannotPutAFavoriteInASelectedDock() {
        val text = "launcher-config\t1\nfav\ta\tA\nprofiles\tWORK\tHOME\tOUT\ndock\tWORK\nslot\tWORK\t0\ta\tA\nslot\tWORK\t1\tb\tB\n"
        assertEquals(listOf(null, DockApp("b", "B"), null), ConfigFormat.decode(text).profiles.dock(WORK).slots)
    }

    @Test
    fun removedFavoriteIsRestoredAtItsPlace() {
        val config = Config(favorites = Favorites(listOf(fav("a"), fav("b"), fav("c"))))
        val restored = config.removeFavorite("b").restoreFavorite(1, fav("b"))
        assertEquals(config, restored)
        // List shortened meanwhile: the place is bounded.
        assertEquals(listOf("a", "b"), config.removeFavorite("b").removeFavorite("c").restoreFavorite(5, fav("b")).favorites.items.map { it.appId })
    }

    @Test
    fun restoreIsIgnoredWhenTheAppMovedToADock() {
        val moved = withProfiles.removeFavorite("f").setDockSlot(WORK, 1, app("f"))
        assertEquals(moved, moved.restoreFavorite(0, fav("f")))
    }
}
