package io.github.tomlmbl.launcher.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

/** Selection and counts of a calendar read. Sample events. */
class TallyTest {

    private val now = 1_000_000_000L
    private val hour = 3_600_000L

    private fun row(id: Long, visible: Boolean = true, canceled: Boolean = false, allDay: Boolean = false, declined: Boolean = false) =
        InstanceRow(Occurrence(id, now + id * hour, now + (id + 1) * hour, allDay, declined, "E$id"), visible, canceled)

    @Test
    fun keepsVisibleLiveOccurrencesAndCountsTheRest() {
        val rows = listOf(
            row(1), row(2, visible = false), row(3, canceled = true), row(4, visible = false),
            row(5, allDay = true), row(6, declined = true), row(7, visible = false, canceled = true),
        )
        val (kept, tally) = Tally.of(rows, now)
        assertEquals(listOf(1L, 5L, 6L), kept.map { it.id })
        assertEquals(Tally(rows = 7, hidden = 3, canceled = 1, kept = 3, allDay = 1, declined = 1, shown = true), tally)
    }

    @Test
    fun logLineHasNumbersOnly() {
        val (_, tally) = Tally.of(listOf(row(1)), now)
        assertEquals("rows=1 hidden=0 canceled=0 kept=1 allDay=0 declined=0 next=true", tally.toString())
    }

    @Test
    fun nothingShownWithoutRows() {
        val (kept, tally) = Tally.of(emptyList(), now)
        assertEquals(emptyList<Occurrence>(), kept)
        assertEquals(false, tally.shown)
    }
}
