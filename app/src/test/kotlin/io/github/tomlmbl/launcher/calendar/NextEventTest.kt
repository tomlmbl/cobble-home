package io.github.tomlmbl.launcher.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** Home's next event and the panel's list. Sample events. */
class NextEventTest {

    private val zone = ZoneId.of("UTC")
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, day, h, m).atZone(zone).toInstant().toEpochMilli()
    // Jeudi 8 octobre 2026, 12:12.
    private val now = at(8, 12, 12)

    private fun ev(begin: Long, end: Long, allDay: Boolean = false, declined: Boolean = false, title: String = "E") =
        Occurrence(id = begin, begin = begin, end = end, allDay = allDay, declined = declined, title = title)

    @Test
    fun ongoingEventComesFirst() {
        val ongoing = ev(at(8, 12), at(8, 13), title = "ongoing")
        val next = ev(at(8, 12, 30), at(8, 13), title = "next")
        assertEquals(ongoing, NextEvent.pick(listOf(next, ongoing), now))
    }

    @Test
    fun earliestStartedOngoingEventWins() {
        val early = ev(at(8, 9), at(8, 18), title = "early")
        val late = ev(at(8, 12), at(8, 13), title = "late")
        assertEquals(early, NextEvent.pick(listOf(late, early), now))
    }

    @Test
    fun otherwiseTheNextOneWithinSevenDays() {
        val tomorrow = ev(at(9, 9, 30), at(9, 9, 45))
        val later = ev(at(10, 10), at(10, 12))
        assertEquals(tomorrow, NextEvent.pick(listOf(later, tomorrow), now))
        assertNull(NextEvent.pick(listOf(ev(at(15, 13), at(15, 14))), now))
    }

    @Test
    fun allDayDeclinedAndFinishedEventsAreSkipped() {
        val events = listOf(
            ev(at(8, 0), at(9, 0), allDay = true),
            ev(at(8, 12), at(8, 13), declined = true),
            ev(at(8, 11), at(8, 12, 12)),
        )
        assertNull(NextEvent.pick(events, now))
    }

    @Test
    fun nextReadLandsOnTheEndOfTheShownEvent() {
        assertEquals(60_000L, NextEvent.readDelay(null, now))
        assertEquals(60_000L, NextEvent.readDelay(now + 5 * 60_000L, now))
        assertEquals(20_000L, NextEvent.readDelay(now + 20_000L, now))
        assertEquals(1_000L, NextEvent.readDelay(now + 10L, now))
    }

    @Test
    fun panelGroupsSevenDaysByDay() {
        val events = listOf(
            ev(at(9, 9, 30), at(9, 9, 45), title = "b"),
            ev(at(8, 14), at(8, 15), title = "a"),
            ev(at(8, 0), at(9, 0), allDay = true, title = "allday"),
            ev(at(7, 20), at(8, 13), title = "since yesterday"),
            ev(at(8, 9), at(8, 10), title = "over"),
            ev(at(8, 12), at(8, 13), declined = true, title = "declined"),
            ev(at(15, 13), at(15, 14), title = "too far"),
            ev(at(15, 9), at(15, 10), title = "eighth day, earlier hour"),
        )
        val days = NextEvent.days(events, now, zone)
        assertEquals(listOf(8, 9), days.map { it.date.dayOfMonth })
        // Declined and all-day events stay in the panel; an event that is over leaves it.
        assertEquals(listOf("allday", "since yesterday", "declined", "a"), days[0].events.map { it.title })
        assertEquals(listOf("b"), days[1].events.map { it.title })
    }

    private fun utcMidnight(day: Int) = LocalDateTime.of(2026, 10, day, 0, 0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
    private fun allDay(day: Int, title: String) = ev(utcMidnight(day), utcMidnight(day + 1), allDay = true, title = title)

    @Test
    fun allDayEventsKeepTheirDateEastOfUtc() {
        val paris = ZoneId.of("Europe/Paris")
        // October 8, 00:30 in Paris: October 7, 22:30 UTC. The event of the 7th is over, the one of the 8th is today.
        val now = LocalDateTime.of(2026, 10, 8, 0, 30).atZone(paris).toInstant().toEpochMilli()
        val days = NextEvent.days(listOf(allDay(7, "yesterday"), allDay(8, "today")), now, paris)
        assertEquals(listOf(8), days.map { it.date.dayOfMonth })
        assertEquals(listOf("today"), days[0].events.map { it.title })
    }

    @Test
    fun allDayEventsKeepTheirDateWestOfUtc() {
        val newYork = ZoneId.of("America/New_York")
        // October 8, 21:00 in New York: October 9, 01:00 UTC. The event of the 8th lasts until midnight, the 9th's is tomorrow.
        val now = LocalDateTime.of(2026, 10, 8, 21, 0).atZone(newYork).toInstant().toEpochMilli()
        val days = NextEvent.days(listOf(allDay(8, "today"), allDay(9, "tomorrow")), now, newYork)
        assertEquals(listOf(8, 9), days.map { it.date.dayOfMonth })
        assertEquals(listOf("today"), days[0].events.map { it.title })
        assertEquals(listOf("tomorrow"), days[1].events.map { it.title })
    }
}
