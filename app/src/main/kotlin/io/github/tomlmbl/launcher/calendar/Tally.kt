package io.github.tomlmbl.launcher.calendar

/** An `Instances` row before selection: the occurrence and what may leave it out. */
class InstanceRow(val occurrence: Occurrence, val visible: Boolean, val canceled: Boolean)

/**
 * Counts from one calendar read, the only content of the `Calendar` log: never a title, a time or a calendar.
 * [rows] occurrences in the range; left out: [hidden] (hidden calendar) and [canceled]; kept: [kept], including
 * [allDay] and [declined]; [shown]: an event was picked for Home.
 */
data class Tally(
    val rows: Int,
    val hidden: Int,
    val canceled: Int,
    val kept: Int,
    val allDay: Int,
    val declined: Int,
    val shown: Boolean,
) {
    override fun toString() =
        "rows=$rows hidden=$hidden canceled=$canceled kept=$kept allDay=$allDay declined=$declined next=$shown"

    companion object {
        /** Occurrences kept for Home and the panel, with their counts. A hidden calendar takes precedence. */
        fun of(rows: List<InstanceRow>, now: Long): Pair<List<Occurrence>, Tally> {
            val hidden = rows.count { !it.visible }
            val shownRows = rows.filter { it.visible }
            val canceled = shownRows.count { it.canceled }
            val kept = shownRows.filter { !it.canceled }.map { it.occurrence }
            return kept to Tally(
                rows = rows.size, hidden = hidden, canceled = canceled, kept = kept.size,
                allDay = kept.count { it.allDay }, declined = kept.count { it.declined },
                shown = NextEvent.pick(kept, now) != null,
            )
        }
    }
}
