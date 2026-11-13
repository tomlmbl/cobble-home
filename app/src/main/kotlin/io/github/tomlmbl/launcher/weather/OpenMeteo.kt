package io.github.tomlmbl.launcher.weather

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * The only external service (see PRIVACY.md): forecast, and city search on request.
 * No key or identifier; blocking calls, to be made off the main thread. Logs neither city nor coordinates.
 */
object OpenMeteo {

    private const val TIMEOUT_MS = 10_000
    /** Replaces the platform's one, which names the device model and build. */
    private const val USER_AGENT = "CobbleHome"

    /**
     * The city's hours and days: "Now" is the current hour, read at display time. 18 hours: the current hour (the
     * first one returned may precede it, as observed), the next 12 until the next request, 3 h later, and one spare.
     * Two days: correct high and low just after midnight.
     */
    fun forecast(city: City, now: Long): Forecast {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${Weather.round(city.latitude)}&longitude=${Weather.round(city.longitude)}" +
            "&hourly=temperature_2m,weather_code,is_day,precipitation_probability&forecast_hours=18" +
            "&daily=temperature_2m_max,temperature_2m_min&forecast_days=2" +
            "&timezone=auto&timeformat=unixtime"
        val json = JSONObject(get(url))
        val hourly = json.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val temperatures = hourly.getJSONArray("temperature_2m")
        val codes = hourly.getJSONArray("weather_code")
        val isDay = hourly.getJSONArray("is_day")
        val rain = hourly.optJSONArray("precipitation_probability")
        val hours = (0 until times.length()).mapNotNull { i ->
            if (temperatures.isNull(i) || codes.isNull(i)) null
            else Hour(
                times.getLong(i) * 1000, temperatures.getDouble(i), codes.getInt(i), isDay.optInt(i, 1) == 1,
                rain?.takeUnless { it.isNull(i) }?.optInt(i),
            )
        }
        val daily = json.optJSONObject("daily")
        val dayTimes = daily?.optJSONArray("time")
        val highs = daily?.optJSONArray("temperature_2m_max")
        val lows = daily?.optJSONArray("temperature_2m_min")
        val days = if (dayTimes == null || highs == null || lows == null) emptyList() else (0 until dayTimes.length()).mapNotNull { i ->
            if (highs.isNull(i) || lows.isNull(i)) null else Day(dayTimes.getLong(i) * 1000, highs.getDouble(i), lows.getDouble(i))
        }
        return Forecast(fetchedAt = now, cityId = city.id, hours = hours, days = days)
    }

    /** Online search, only when "Search online" is tapped: sends [name] and [language]. */
    fun search(name: String, language: String): List<City> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=10&format=json" +
            "&name=${URLEncoder.encode(name.trim(), "UTF-8")}&language=${URLEncoder.encode(language, "UTF-8")}"
        val results = JSONObject(get(url)).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val r = results.getJSONObject(i)
            if (!r.has("latitude") || !r.has("longitude")) return@mapNotNull null
            val label = r.optString("name").ifEmpty { return@mapNotNull null }
            City(
                id = r.optLong("id"),
                name = label,
                ascii = label,
                country = r.optString("country_code"),
                region = r.optString("admin1"),
                latitude = r.getDouble("latitude"),
                longitude = r.getDouble("longitude"),
                population = r.optLong("population"),
            )
        }
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            return connection.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            connection.disconnect()
        }
    }
}
