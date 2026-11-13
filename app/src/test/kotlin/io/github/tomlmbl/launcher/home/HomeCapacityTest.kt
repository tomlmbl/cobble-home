package io.github.tomlmbl.launcher.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCapacityTest {

    @Test
    fun countsWholeRowsBetweenClockAndDock() {
        // 500 high, clock 100, gaps 72: 328 / 52 = 6 whole rows.
        assertEquals(6, favoriteCapacity(500f, 100f, 72f, 52f))
    }

    @Test
    fun capsAtMaxFavorites() {
        // 1,200 high: 1,028 / 52 = 19 rows would fit, the cap keeps 6.
        assertEquals(MAX_FAVORITES, favoriteCapacity(1200f, 100f, 72f, 52f))
        assertEquals(6, MAX_FAVORITES)
    }

    @Test
    fun keepsAtLeastOneRow() {
        assertEquals(1, favoriteCapacity(100f, 100f, 72f, 52f))
    }

    /** Without profiles, Home gains the dock's room, but the number of shown favorites does not change. */
    @Test
    fun turningProfilesOffNeverChangesShownFavorites() {
        val freed = 104f * 2.625f
        for (docked in listOf(300f, 420f, 455f, 600f)) {
            val withDock = favoriteCapacity(capacityHeight(docked, true, freed), 100f, 72f, 52f)
            val withoutDock = favoriteCapacity(capacityHeight(docked + freed, false, freed), 100f, 72f, 52f)
            assertEquals(withDock, withoutDock)
        }
    }
}
