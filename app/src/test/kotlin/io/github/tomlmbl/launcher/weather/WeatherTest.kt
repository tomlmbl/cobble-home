package io.github.tomlmbl.launcher.weather

import io.github.tomlmbl.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Refresh and current hour, following hours, glyphs and units. Staleness: home/GlanceTest. */
class WeatherTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun aFetchInTheFutureIsFetchedAgain() {
        // Clock set back after the read: the age is unknown, the value is requested again.
        assertTrue(Weather.needsRefresh(fetchedAt = 2 * hour, now = hour))
    }

    @Test
    fun refreshAfterThreeHours() {
        assertTrue(Weather.needsRefresh(fetchedAt = null, now = 0))
        assertFalse(Weather.needsRefresh(fetchedAt = 0, now = 3 * hour))
        assertTrue(Weather.needsRefresh(fetchedAt = 0, now = 3 * hour + 1))
    }

    @Test
    fun nowIsTheHourThatContainsTheClock() {
        val base = 1_000 * hour
        val hours = (-1..17).map { Hour(base + it * hour, it.toDouble(), 0, true) }
        assertEquals(0.0, Weather.hourAt(hours, base)!!.temperature, 0.0)
        assertEquals(0.0, Weather.hourAt(hours, base + 59 * minute)!!.temperature, 0.0)
        // Three hours after the request, "Now" and the next 12 hours stay covered.
        val later = base + 3 * hour + 59 * minute
        assertEquals(3.0, Weather.hourAt(hours, later)!!.temperature, 0.0)
        assertEquals(12, Weather.nextHours(hours, later).size)
    }

    @Test
    fun noNowOutsideTheForecast() {
        val hours = (0..2).map { Hour(it * hour, 10.0, 0, true) }
        assertNull(Weather.hourAt(hours, 3 * hour))
        assertNull(Weather.hourAt(hours, -1))
    }

    @Test
    fun theDayFollowsMidnightOfTheCity() {
        val day = 24 * hour
        val days = listOf(Day(0, 14.0, 6.0), Day(day, 9.0, 1.0))
        assertEquals(14.0, Weather.dayAt(days, day - 1)!!.high, 0.0)
        assertEquals(9.0, Weather.dayAt(days, day)!!.high, 0.0)
        assertNull(Weather.dayAt(days, 2 * day + hour))
        assertNull(Weather.dayAt(days, -1))
    }

    @Test
    fun nextTwelveHoursStartAfterTheCurrentHour() {
        val base = 1_000 * hour
        val hours = (-2..20).map { Hour(base + it * hour, 10.0, 0, true) }
        val next = Weather.nextHours(hours, now = base + 20 * minute)
        assertEquals(12, next.size)
        assertEquals(base + hour, next.first().time)
        assertEquals(base + 12 * hour, next.last().time)
    }

    @Test
    fun halfHourZonesKeepTheCurrentHourOut() {
        // +5:30 time zone: local hours fall on the UTC half hour; at 13:10 local, 13:00 local is "Now".
        val localOne = 1_000 * hour + 30 * minute
        val hours = (0..14).map { Hour(localOne + it * hour, 10.0, 0, true) }
        val next = Weather.nextHours(hours, now = localOne + 10 * minute)
        assertEquals(localOne + hour, next.first().time)
        assertEquals(12, next.size)
    }

    @Test
    fun fewerHoursWhenTheForecastRunsOut() {
        val hours = (0..3).map { Hour(it * hour, 10.0, 0, true) }
        assertEquals(3, Weather.nextHours(hours, now = 0).size)
    }

    @Test
    fun wmoCodesMapToSkies() {
        assertEquals(Sky.CLEAR, Sky.of(0))
        assertEquals(Sky.PARTLY_CLOUDY, Sky.of(1))
        assertEquals(Sky.PARTLY_CLOUDY, Sky.of(2))
        assertEquals(Sky.CLOUDY, Sky.of(3))
        assertEquals(Sky.FOG, Sky.of(48))
        assertEquals(Sky.DRIZZLE, Sky.of(53))
        assertEquals(Sky.RAIN, Sky.of(63))
        assertEquals(Sky.RAIN, Sky.of(81))
        assertEquals(Sky.FREEZING_RAIN, Sky.of(57))
        assertEquals(Sky.FREEZING_RAIN, Sky.of(66))
        assertEquals(Sky.SNOW, Sky.of(75))
        assertEquals(Sky.SNOW, Sky.of(86))
        assertEquals(Sky.THUNDERSTORM, Sky.of(95))
        assertEquals(Sky.THUNDERSTORM, Sky.of(99))
        assertEquals(Sky.UNKNOWN, Sky.of(42))
    }

    @Test
    fun glyphsFollowDayAndNight() {
        assertEquals(R.drawable.ic_sunny, Sky.CLEAR.icon(isDay = true))
        assertEquals(R.drawable.ic_clear_night, Sky.CLEAR.icon(isDay = false))
        assertEquals(R.drawable.ic_partly_cloudy_day, Sky.PARTLY_CLOUDY.icon(isDay = true))
        assertEquals(R.drawable.ic_partly_cloudy_night, Sky.PARTLY_CLOUDY.icon(isDay = false))
        assertEquals(R.drawable.ic_cloud, Sky.CLOUDY.icon(isDay = false))
    }

    @Test
    fun picturesFollowDayAndNightAndSeparateDrizzle() {
        assertEquals(R.drawable.weather_clear_day, Sky.CLEAR.picture(isDay = true))
        assertEquals(R.drawable.weather_clear_night, Sky.CLEAR.picture(isDay = false))
        assertEquals(R.drawable.weather_partly_cloudy_night, Sky.PARTLY_CLOUDY.picture(isDay = false))
        assertEquals(R.drawable.weather_drizzle, Sky.DRIZZLE.picture(isDay = true))
        assertEquals(R.drawable.weather_rain, Sky.RAIN.picture(isDay = true))
        assertEquals(R.drawable.weather_sleet, Sky.FREEZING_RAIN.picture(isDay = false))
        assertEquals(R.drawable.weather_cloudy, Sky.UNKNOWN.picture(isDay = true))
    }

    @Test
    fun unitFollowsTheRegionalPreferenceThenTheCountry() {
        assertEquals(TemperatureUnit.FAHRENHEIT, TemperatureUnit.of(preference = "fahrenhe", country = "FR"))
        assertEquals(TemperatureUnit.CELSIUS, TemperatureUnit.of(preference = "celsius", country = "US"))
        assertEquals(TemperatureUnit.CELSIUS, TemperatureUnit.of(preference = "kelvin", country = "US"))
        assertEquals(TemperatureUnit.FAHRENHEIT, TemperatureUnit.of(preference = null, country = "US"))
        assertEquals(TemperatureUnit.FAHRENHEIT, TemperatureUnit.of(preference = null, country = "pr"))
        assertEquals(TemperatureUnit.CELSIUS, TemperatureUnit.of(preference = null, country = "GB"))
        assertEquals(TemperatureUnit.CELSIUS, TemperatureUnit.of(preference = null, country = ""))
    }

    @Test
    fun temperaturesAreRoundedInTheChosenUnit() {
        assertEquals("11°", TemperatureUnit.CELSIUS.format(10.6))
        assertEquals("-3°", TemperatureUnit.CELSIUS.format(-2.6))
        assertEquals("0°", TemperatureUnit.CELSIUS.format(-0.4))
        assertEquals("51°", TemperatureUnit.FAHRENHEIT.format(10.6))
    }

    @Test
    fun coordinatesAreRoundedToTwoDecimals() {
        assertEquals(52.52, Weather.round(52.52437), 0.0)
        assertEquals(13.41, Weather.round(13.41053), 0.0)
        assertEquals(-33.87, Weather.round(-33.8688), 0.0)
    }

    // Likely rain: a forecast hour's probability covers the hour before it.
    private val midnight = 1_000 * 24 * hour
    private val today = Day(midnight, 15.0, 6.0)
    private val tomorrow = Day(midnight + 24 * hour, 15.0, 6.0)
    private fun rain(vararg probabilities: Pair<Int, Int>) =
        (0..30).map { h -> Hour(midnight + h * hour, 10.0, 0, true, probabilities.toMap()[h] ?: 0) }

    @Test
    fun rainLikelyFromTheStartOfTheHourItCovers() {
        val now = midnight + 10 * hour
        // 60 % at the 15:00 point: rain between 14:00 and 15:00.
        assertEquals(midnight + 14 * hour, Weather.rainFrom(rain(15 to 60), today, listOf(today, tomorrow), now))
    }

    @Test
    fun rainUnderTheThresholdIsIgnored() {
        val now = midnight + 10 * hour
        assertNull(Weather.rainFrom(rain(15 to 49), today, listOf(today, tomorrow), now))
        assertEquals(midnight + 14 * hour, Weather.rainFrom(rain(15 to 50), today, listOf(today, tomorrow), now))
    }

    @Test
    fun rainAlreadyStartedIsNow() {
        val now = midnight + 10 * hour + 20 * minute
        // The 11:00 point covers 10:00 to 11:00, the current hour.
        assertEquals(now, Weather.rainFrom(rain(11 to 80), today, listOf(today, tomorrow), now))
        // The 10:00 one covers a past hour.
        assertNull(Weather.rainFrom(rain(10 to 80), today, listOf(today, tomorrow), now))
    }

    @Test
    fun rainTomorrowIsNotToday() {
        val now = midnight + 20 * hour
        // The midnight point covers 23:00 to midnight: still today; the 01:00 one, tomorrow.
        assertEquals(midnight + 23 * hour, Weather.rainFrom(rain(24 to 70), today, listOf(today, tomorrow), now))
        assertNull(Weather.rainFrom(rain(25 to 70), today, listOf(today, tomorrow), now))
        // Without a known day, no line.
        assertNull(Weather.rainFrom(rain(15 to 70), null, emptyList(), now))
    }

    @Test
    fun missingProbabilityIsNoRain() {
        val hours = (0..30).map { h -> Hour(midnight + h * hour, 10.0, 0, true) }
        assertNull(Weather.rainFrom(hours, today, listOf(today), midnight + 10 * hour))
    }
}
