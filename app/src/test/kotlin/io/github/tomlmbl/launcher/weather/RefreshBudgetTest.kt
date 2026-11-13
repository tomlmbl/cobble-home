package io.github.tomlmbl.launcher.weather

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cap on automatic requests (PRIVACY.md). */
class RefreshBudgetTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun neverMoreThanEightRequestsInADay() {
        val budget = RefreshBudget()
        var now = 0L
        var count = 0
        // Repeated failures, Home always visible: one attempt every minute if the budget allows it.
        while (now < 24 * hour) {
            if (budget.allows(now)) { budget.sent(now); budget.failed(now); count++ }
            now += minute
        }
        assertTrue(count <= RefreshBudget.MAX_PER_DAY)
    }

    @Test
    fun retriesBackOffAfterEachFailure() {
        val budget = RefreshBudget()
        budget.sent(0); budget.failed(0)
        assertFalse(budget.allows(5 * minute - 1))
        assertTrue(budget.allows(5 * minute))
        budget.sent(5 * minute); budget.failed(5 * minute)
        assertFalse(budget.allows(15 * minute - 1))
        assertTrue(budget.allows(15 * minute))
    }

    @Test
    fun aSuccessEndsTheBackOff() {
        val budget = RefreshBudget()
        budget.sent(0); budget.failed(0)
        budget.succeeded()
        assertTrue(budget.allows(1))
    }

    @Test
    fun theWindowSlides() {
        val budget = RefreshBudget()
        repeat(RefreshBudget.MAX_PER_DAY) { budget.sent(it * 3 * hour) }
        assertFalse(budget.allows(21 * hour + 1))
        assertTrue(budget.allows(24 * hour))
    }
}
