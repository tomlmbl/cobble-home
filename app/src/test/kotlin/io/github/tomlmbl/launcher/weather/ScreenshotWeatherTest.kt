package io.github.tomlmbl.launcher.weather

import io.github.tomlmbl.launcher.home.Freshness
import io.github.tomlmbl.launcher.home.freshness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Sample weather of the `screenshots` build: fresh, daytime and complete at the displayed 16:00. */
class ScreenshotWeatherTest {

    private val zone = ZoneId.systemDefault()
    private val now = LocalDate.of(2026, 11, 12).atTime(16, 0).atZone(zone).toInstant().toEpochMilli()
    private val forecast = ScreenshotWeather.data(now).current!!

    @Test
    fun freshAndDaytimeAtTheShownTime() {
        assertEquals(Freshness.FRESH, freshness(forecast.fetchedAt, now))
        val current = forecast.now(now)!!
        assertEquals(14.0, current.temperature, 0.0)
        assertTrue(current.isDay)
        assertNotNull(forecast.day(now))
    }

    @Test
    fun twelveHoursFollowAndRainStaysUnlikely() {
        assertEquals(12, Weather.nextHours(forecast.hours, now).size)
        assertTrue(forecast.hours.all { (it.rain ?: 0) < 50 })
    }
}
