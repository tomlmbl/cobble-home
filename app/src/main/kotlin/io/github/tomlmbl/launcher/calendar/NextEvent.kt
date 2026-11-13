package io.github.tomlmbl.launcher.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** An event occurrence read from the calendar, kept in memory only. */
data class Occurrence(
    val id: Long,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val declined: Boolean,
    val title: String,
)

class AgendaDay(val date: LocalDate, val events: List<Occurrence>)

object NextEvent {

    private const val MINUTE = 60_000L
    const val DAYS = 7
    private const val MIN_DELAY = 1_000L

    private fun horizon(now: Long) = now + DAYS * 24 * 60 * MINUTE

    /**
     * Home: the ongoing event, the earliest started if there are several, otherwise the next one within [DAYS]
     * days; all-day and declined events excluded.
     */
    fun pick(events: List<Occurrence>, now: Long): Occurrence? {
        val timed = events.filter { !it.allDay && !it.declined && it.end > now }
        return timed.filter { it.begin <= now }.minByOrNull { it.begin }
            ?: timed.filter { it.begin < horizon(now) }.minByOrNull { it.begin }
    }

    /**
     * Delay before the next read: one minute, or the end of the displayed event if sooner, so that the next event
     * replaces it without an empty line; at least one second.
     */
    fun readDelay(shownEnd: Long?, now: Long): Long =
        if (shownEnd != null && shownEnd - now in 0 until MINUTE) maxOf(shownEnd - now, MIN_DELAY) else MINUTE

    /**
     * Panel: ongoing or upcoming events over [DAYS] days, grouped by start day; an event started before today is
     * listed under today. All-day events first, then by start time. An all-day event is stored in UTC, midnight to
     * midnight (`CalendarContract.Events`): its days are read in UTC.
     */
    fun days(events: List<Occurrence>, now: Long, zone: ZoneId): List<AgendaDay> {
        val today = date(now, zone)
        val last = today.plusDays(DAYS - 1L)
        return events.asSequence()
            .mapNotNull { event ->
                if (event.allDay) {
                    val first = date(event.begin, ZoneOffset.UTC)
                    val end = date(event.end, ZoneOffset.UTC)
                    if (end > today && first <= last) maxOf(today, first) to event else null
                } else {
                    // Bounded by [last] like all-day events: no partial eighth day.
                    val first = date(event.begin, zone)
                    if (event.end > now && first <= last) maxOf(today, first) to event else null
                }
            }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
            .map { (date, list) -> AgendaDay(date, list.sortedWith(compareBy<Occurrence> { !it.allDay }.thenBy { it.begin })) }
    }

    private fun date(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
