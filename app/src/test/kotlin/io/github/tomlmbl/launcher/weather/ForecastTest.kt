package io.github.tomlmbl.launcher.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Weather file and current city. Sample place at Open-Meteo's example coordinates. */
class ForecastTest {

    private val city = City(7, "Exampleton", "Exampleton", "AA", "North", 52.52, 13.41, 20_000)
    private val other = city.copy(id = 8, name = "Elsewhere", ascii = "Elsewhere")
    private val forecast = Forecast(
        fetchedAt = 1_000L, cityId = 7,
        // Rain probability on one hour only: the field is optional.
        hours = listOf(Hour(3_600_000L, 12.0, 3, true, rain = 40), Hour(7_200_000L, -1.5, 71, false)),
        days = listOf(Day(0L, 14.0, 6.2), Day(86_400_000L, 9.5, -2.0)),
    )

    @Test
    fun roundTrip() {
        val data = WeatherData(WeatherMode.LOCATION, city, forecast)
        assertEquals(data, WeatherFormat.decode(WeatherFormat.encode(data)))
        assertEquals(WeatherData(), WeatherFormat.decode(WeatherFormat.encode(WeatherData())))
    }

    @Test
    fun aForecastWithoutDaysIsKept() {
        val data = WeatherData(WeatherMode.CITY, city, forecast.copy(days = emptyList()))
        assertEquals(data, WeatherFormat.decode(WeatherFormat.encode(data)))
    }

    @Test
    fun aVersionOneFileKeepsItsHoursAndDropsUndatedLines() {
        // Older format: current conditions ("now") and a day without a date; only the hours remain.
        val v1 = "launcher-weather\t1\nmode\tCITY\ncity\t" + Cities.encode(city) + "\nfetched\t1000\t7\n" +
            "now\t900\t11.5\t2\t1\nday\t14.0\t6.2\nhour\t3600000\t12.0\t3\t1\n"
        val decoded = WeatherFormat.decode(v1).forecast
        assertEquals(Forecast(1_000L, 7, listOf(Hour(3_600_000L, 12.0, 3, true))), decoded)
    }

    @Test
    fun unreadableFileMeansWeatherOff() {
        assertEquals(WeatherData(), WeatherFormat.decode("garbage\nmode\tCITY\n"))
        assertEquals(WeatherMode.OFF, WeatherFormat.decode("mode\tSOMETHING\ncity\t" + Cities.encode(city)).mode)
    }

    @Test
    fun weatherOfAnotherCityIsNotShown() {
        assertEquals(forecast, WeatherData(WeatherMode.CITY, city, forecast).current)
        assertNull(WeatherData(WeatherMode.CITY, other, forecast).current)
        assertNull(WeatherData(WeatherMode.CITY, city, forecast).off().current)
    }

    @Test
    fun choosingACityLeavesLocationMode() {
        val located = WeatherData().located(city)
        assertEquals(WeatherMode.LOCATION, located.mode)
        assertEquals(WeatherData(WeatherMode.CITY, other), located.choose(other))
    }
}
