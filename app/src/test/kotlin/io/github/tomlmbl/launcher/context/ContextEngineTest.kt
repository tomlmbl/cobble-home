package io.github.tomlmbl.launcher.context

import io.github.tomlmbl.launcher.context.Profile.HOME
import io.github.tomlmbl.launcher.context.Profile.OUT
import io.github.tomlmbl.launcher.context.Profile.TRAVEL
import io.github.tomlmbl.launcher.context.Profile.WORK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContextEngineTest {

    private val three = ContextState(Profiles(listOf(WORK, HOME, OUT), active = WORK))

    @Test
    fun manualChoiceAppliesAtOnce() {
        assertEquals(HOME, ContextEngine.choose(three, HOME).active)
    }

    @Test
    fun choosingAProfileOutsideTheSelectionChangesNothing() {
        assertEquals(three, ContextEngine.choose(three, TRAVEL))
    }

    @Test
    fun proposedChangeWaitsForTheSafeMoment() {
        val pending = ContextEngine.propose(three, OUT)
        assertEquals(WORK, pending.active)
        assertEquals(OUT, pending.pending)
        val applied = ContextEngine.atSafeMoment(pending)
        assertEquals(OUT, applied.active)
        assertNull(applied.pending)
    }

    @Test
    fun manualChoiceCancelsAPendingChange() {
        val chosen = ContextEngine.choose(ContextEngine.propose(three, OUT), HOME)
        assertEquals(HOME, ContextEngine.atSafeMoment(chosen).active)
    }

    @Test
    fun proposingTheActiveProfileLeavesNothingPending() {
        assertNull(ContextEngine.propose(three, WORK).pending)
    }

    @Test
    fun withoutProfilesNothingIsActiveAndNothingSwitches() {
        val none = ContextState(Profiles())
        assertNull(none.active)
        assertEquals(none, ContextEngine.choose(none, WORK))
        assertNull(ContextEngine.atSafeMoment(ContextEngine.propose(none, WORK)).active)
    }

    @Test
    fun pendingProfileRemovedBeforeTheSafeMomentIsDropped() {
        val pending = ContextEngine.propose(three, OUT)
        val removed = pending.copy(profiles = pending.profiles.select(listOf(WORK, HOME, TRAVEL)) { false })
        val applied = ContextEngine.atSafeMoment(removed)
        assertEquals(WORK, applied.active)
        assertNull(applied.pending)
    }
}
