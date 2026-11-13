package io.github.tomlmbl.launcher.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

/** Staleness of Home's weather and the event's time. Sample events. */
class GlanceTest {

    private val hour = 3_600_000L
    private fun at(fetchedAt: Long) = WeatherGlance(listOf(GlanceHour(0, icon = 0, temperature = "11°")), fetchedAt = fetchedAt)

    @Test
    fun weatherDimmedAfterFourHoursAndHiddenAfterTwelve() {
        assertEquals(Freshness.FRESH, at(0).freshness(now = 4 * hour))
        assertEquals(Freshness.AGED, at(0).freshness(now = 4 * hour + 1))
        assertEquals(Freshness.AGED, at(0).freshness(now = 12 * hour))
        assertEquals(Freshness.HIDDEN, at(0).freshness(now = 12 * hour + 1))
    }

    @Test
    fun theHomeShowsTheCurrentHour() {
        val glance = WeatherGlance((0..17).map { GlanceHour(it * hour, 0, "$it°") }, fetchedAt = 0)
        assertEquals("3°", glance.at(3 * hour + 30 * 60_000)!!.temperature)
        assertNull(glance.at(18 * hour))
    }

    @Test
    fun aFetchInTheFutureIsNotTrustedAsFresh() {
        assertEquals(Freshness.AGED, at(2 * hour).freshness(now = hour))
    }

    @Test
    fun aFetchAfterTheLastClockTickIsFresh() {
        assertEquals(Freshness.FRESH, at(hour + 40_000).freshness(now = hour))
    }

    private val zone = ZoneId.of("UTC")
    private fun on(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, day, h, m).atZone(zone).toInstant().toEpochMilli()
    // Jeudi 8 octobre 2026, 12:12.
    private val now = on(8, 12, 12)
    private fun ev(begin: Long, end: Long) = EventGlance("E", begin, end)

    @Test
    fun anEventStartingNowIsOngoing() {
        assertEquals(EventTime.Until(on(8, 13)), ev(now, on(8, 13)).time(now, zone))
    }

    @Test
    fun labelsDependOnHowFarTheEventIs() {
        assertEquals(EventTime.Until(on(8, 13, 15)), ev(on(8, 12), on(8, 13, 15)).time(now, zone))
        assertEquals(EventTime.InMinutes(30), ev(on(8, 12, 42), on(8, 13)).time(now, zone))
        assertEquals(EventTime.InMinutes(1), ev(now + 20_000, on(8, 13)).time(now, zone))
        assertEquals(EventTime.Today(on(8, 13, 12)), ev(on(8, 13, 12), on(8, 14)).time(now, zone))
        assertEquals(EventTime.Today(on(8, 14)), ev(on(8, 14), on(8, 15)).time(now, zone))
        assertEquals(EventTime.Tomorrow(on(9, 9, 30)), ev(on(9, 9, 30), on(9, 9, 45)).time(now, zone))
        assertEquals(EventTime.Later(DayOfWeek.SATURDAY, on(10, 10)), ev(on(10, 10), on(10, 12)).time(now, zone))
    }

    @Test
    fun aFinishedEventIsNoLongerShown() {
        assertNull(ev(on(8, 11), now).time(now, zone))
    }
}
