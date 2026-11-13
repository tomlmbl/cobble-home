package io.github.tomlmbl.launcher.weather

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Sample weather for the `screenshots` build: Rouen, partly cloudy, 14° at the displayed time, forecast "received"
 * at that time. Day up to and including the 17:00 hour (sunset around 17:15 in November), then night.
 */
object ScreenshotWeather {

    private val ROUEN = City(2982652, "Rouen", "Rouen", "FR", "Normandy", 49.44, 1.10, 116331)
    private const val PARTLY_CLOUDY = 2
    private val TEMPERATURES = listOf(14, 13, 12, 11, 11, 10, 10, 9, 9, 9, 8, 8, 8, 7, 7, 7, 7, 8)
    private val RAIN = listOf(0, 0, 5, 5, 10, 10, 10, 15, 15, 10, 10, 5, 5, 5, 0, 0, 0, 0)

    fun data(now: Long): WeatherData {
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(now).truncatedTo(ChronoUnit.HOURS).atZone(zone)
        val hours = TEMPERATURES.mapIndexed { i, t ->
            val time = start.plusHours(i.toLong())
            Hour(time.toInstant().toEpochMilli(), t.toDouble(), PARTLY_CLOUDY, isDay = time.hour in 8..17, rain = RAIN[i])
        }
        val today = start.toLocalDate()
        val days = listOf(Day(midnight(today, zone), 15.0, 7.0), Day(midnight(today.plusDays(1), zone), 13.0, 6.0))
        return WeatherData(WeatherMode.CITY, ROUEN, Forecast(fetchedAt = now, cityId = ROUEN.id, hours = hours, days = days))
    }

    private fun midnight(date: LocalDate, zone: ZoneId) = date.atStartOfDay(zone).toInstant().toEpochMilli()
}
