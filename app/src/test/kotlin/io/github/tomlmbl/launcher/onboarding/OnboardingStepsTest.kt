package io.github.tomlmbl.launcher.onboarding

import io.github.tomlmbl.launcher.context.Profile.HOME
import io.github.tomlmbl.launcher.context.Profile.OUT
import io.github.tomlmbl.launcher.context.Profile.WORK
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingStepsTest {

    @Test
    fun defaultPathCountsThreeProfilesBeforeTheChoice() {
        assertEquals(6, onboardingSteps(null, roleHeld = false).size)
        assertEquals(5, onboardingSteps(null, roleHeld = true).size)
    }

    @Test
    fun totalIsRecomputedAfterTheChoice() {
        assertEquals(3, onboardingSteps(emptyList(), roleHeld = false).size)
        assertEquals(2, onboardingSteps(emptyList(), roleHeld = true).size)
        val steps = onboardingSteps(listOf(OUT, WORK, HOME), roleHeld = false)
        assertEquals(listOf(Step.Profiles, Step.Apps(OUT), Step.Apps(WORK), Step.Apps(HOME), Step.Favorites, Step.Role), steps)
    }
}
