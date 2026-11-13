package io.github.tomlmbl.launcher.context

import io.github.tomlmbl.launcher.context.Profile.HOME
import io.github.tomlmbl.launcher.context.Profile.OUT
import io.github.tomlmbl.launcher.context.Profile.TRAVEL
import io.github.tomlmbl.launcher.context.Profile.WORK
import io.github.tomlmbl.launcher.context.Profile.WORKOUT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilesTest {

    private fun app(id: String) = DockApp(id, "Label $id")
    private fun dock(vararg ids: String?) = Dock(ids.map { it?.let(::app) })
    private fun Dock.ids() = slots.map { it?.appId }
    private val none: (String) -> Boolean = { false }

    @Test
    fun dockKeepsThreeFixedSlots() {
        val d = dock("a", null, "c")
        assertEquals(listOf("a", "x", "c"), d.set(1, app("x")).ids())
        assertEquals(listOf("a", null, null), d.clear(2).ids())
        assertEquals(1, d.firstFree)
        assertNull(dock("a", "b", "c").firstFree)
    }

    @Test
    fun anAppAppearsOnlyOnceInADock() {
        assertEquals(listOf("a", null, "c"), dock("a", null, "c").set(1, app("a")).ids())
        assertEquals(listOf("a", null, "c"), dock("a", null, "c").set(0, app("a")).ids())
    }

    @Test
    fun forgettingAnAppLeavesAPlusWithoutShiftingTheOthers() {
        assertEquals(listOf("a", null, "c"), dock("a", "b", "c").forget { it == "b" }.ids())
    }

    @Test
    fun firstSelectionKeepsTheTouchOrderAndActivatesTheFirst() {
        val p = Profiles().select(listOf(OUT, WORK, HOME), none)
        assertEquals(listOf(OUT, WORK, HOME), p.selected)
        assertEquals(OUT, p.active)
    }

    @Test
    fun replacedProfileTakesThePlaceOfTheRemovedOne() {
        val p = Profiles(listOf(WORK, HOME, OUT), active = HOME).select(listOf(WORK, TRAVEL, OUT), none)
        assertEquals(listOf(WORK, TRAVEL, OUT), p.selected)
        assertEquals(TRAVEL, p.active)
    }

    @Test
    fun severalSwapsFillRemovedPlacesInOrderWithAddedProfilesInTouchOrder() {
        val p = Profiles(listOf(WORK, HOME, OUT), active = OUT).select(listOf(HOME, TRAVEL, WORKOUT), none)
        assertEquals(listOf(TRAVEL, HOME, WORKOUT), p.selected)
        assertEquals(WORKOUT, p.active)
    }

    @Test
    fun keptActiveProfileStaysActive() {
        val p = Profiles(listOf(WORK, HOME, OUT), active = WORK).select(listOf(WORK, HOME, TRAVEL), none)
        assertEquals(WORK, p.active)
    }

    @Test
    fun removedProfileDockSleepsAndComesBackUnchanged() {
        val start = Profiles(listOf(WORK, HOME, OUT), WORK, mapOf(OUT to dock("m", "n", null)))
        val swapped = start.select(listOf(WORK, HOME, TRAVEL), none)
        assertEquals(listOf("m", "n", null), swapped.dock(OUT).ids())
        assertFalse(swapped.inSelectedDock("m"))

        val back = swapped.select(listOf(WORK, HOME, OUT), none)
        assertEquals(listOf("m", "n", null), back.dock(OUT).ids())
        assertTrue(back.inSelectedDock("m"))
    }

    @Test
    fun appThatBecameFavoriteWhileAsleepLeavesAPlusOnReturn() {
        val asleep = Profiles(listOf(WORK, HOME, TRAVEL), WORK, mapOf(OUT to dock("m", "n", "o")))
        val back = asleep.select(listOf(WORK, HOME, OUT)) { it == "n" }
        assertEquals(listOf("m", null, "o"), back.dock(OUT).ids())
    }

    @Test
    fun disablingProfilesKeepsEveryDockAsleep() {
        val start = Profiles(listOf(WORK, HOME, OUT), HOME, mapOf(WORK to dock("a", null, null), HOME to dock("b", null, null)))
        val off = start.select(emptyList(), none)
        assertFalse(off.enabled)
        assertNull(off.active)
        assertFalse(off.inSelectedDock("a"))
        assertEquals(start.docks, off.docks)

        val on = off.select(listOf(WORK, HOME, OUT), none)
        assertEquals(listOf("a", null, null), on.dock(WORK).ids())
    }

    @Test
    fun onlyProfilesWithoutAKeptDockNeedTheAppStep() {
        val before = Profiles(listOf(WORK, HOME, OUT), WORK, mapOf(TRAVEL to Dock()))
        val after = before.select(listOf(TRAVEL, HOME, WORKOUT), none)
        assertEquals(listOf(WORKOUT), after.needingApps(before))
    }

    @Test
    fun forgetReachesSleepingDocksToo() {
        val p = Profiles(listOf(WORK, HOME, OUT), WORK, mapOf(WORK to dock("a", "b", null), TRAVEL to dock("b", null, null)))
        val forgotten = p.forget { it == "b" }
        assertEquals(listOf("a", null, null), forgotten.dock(WORK).ids())
        assertEquals(listOf(null, null, null), forgotten.dock(TRAVEL).ids())
    }

    @Test
    fun suggestionsFollowTheStaticListAndOnlyInstalledApps() {
        val installed = listOf("x.unknown", "com.spotify.music", "com.strava")
        assertEquals(listOf("com.strava", "com.spotify.music"), Suggestions.forProfile(WORKOUT, installed) { it })
        assertEquals(emptyList<String>(), Suggestions.forProfile(WORK, installed) { it })
    }
}
