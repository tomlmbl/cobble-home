package io.github.tomlmbl.launcher.weather

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.tomlmbl.launcher.R
import io.github.tomlmbl.launcher.home.hourAt
import kotlin.math.roundToInt

/**
 * One forecast hour; [time]: start of the hour, in milliseconds since the epoch. Temperatures in °C. [rain]:
 * probability of more than 0.1 mm of precipitation during the hour **preceding** [time], in % (Open-Meteo's
 * definition); null if the provider does not give it.
 */
data class Hour(val time: Long, val temperature: Double, val code: Int, val isDay: Boolean, val rain: Int? = null)

/** High and low of one day in the city; [time]: the city's local midnight, in milliseconds since the epoch. */
data class Day(val time: Long, val high: Double, val low: Double)

object Weather {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE

    /** Requested again after 3 h, with no value, or if the reading is dated in the future. */
    fun needsRefresh(fetchedAt: Long?, now: Long) = fetchedAt == null || now - fetchedAt !in 0..3 * HOUR

    /** "Now": the forecast hour containing [now], null if the forecast does not cover it. */
    fun hourAt(hours: List<Hour>, now: Long) = hours.hourAt(now) { it.time }

    /** The city's day containing [now] (up to 25 h, on a DST change day), null outside the forecast. */
    fun dayAt(days: List<Day>, now: Long) = days.filter { it.time <= now }.maxByOrNull { it.time }?.takeIf { now - it.time < 25 * HOUR }

    /**
     * The [count] hours after the current hour ("Now" first, then 12 hours). Compared with [now], not with the UTC
     * hour: forecast hours follow the city's time zone, sometimes offset by half an hour.
     */
    fun nextHours(hours: List<Hour>, now: Long, count: Int = 12) = hours.filter { it.time > now }.sortedBy { it.time }.take(count)

    /** "Likely rain" threshold: 50 %. */
    const val RAIN_LIKELY = 50

    /**
     * Likely rain during the city's day: start of the first hour, ongoing or upcoming before the end of [day], whose
     * probability reaches [RAIN_LIKELY]; [now] if it has already started; null otherwise. A forecast hour's
     * probability covers the hour before it.
     */
    fun rainFrom(hours: List<Hour>, day: Day?, days: List<Day>, now: Long): Long? {
        if (day == null) return null
        val end = days.filter { it.time > day.time }.minOfOrNull { it.time } ?: (day.time + 24 * HOUR)
        return hours.filter { (it.rain ?: 0) >= RAIN_LIKELY && it.time > now && it.time - HOUR < end }
            .minOfOrNull { it.time - HOUR }?.coerceAtLeast(now)
    }

    /** Coordinates sent to the provider: 2 decimals, about 1 km. */
    fun round(coordinate: Double) = Math.round(coordinate * 100) / 100.0
}

/**
 * Automatic forecast requests (PRIVACY.md): at most [MAX_PER_DAY] over a rolling 24 h, successful or not; after a
 * failure, a 5 min wait doubled at each new failure, up to 3 h. A city change is not counted ("plus one request
 * when you change city"). Kept in memory: a process restart resets it.
 */
class RefreshBudget {

    private val sent = ArrayDeque<Long>()
    private var failures = 0
    private var failedAt = 0L

    fun allows(now: Long): Boolean {
        while (sent.isNotEmpty() && now - sent.first() !in 0 until DAY) sent.removeFirst()
        return sent.size < MAX_PER_DAY && (failures == 0 || now - failedAt !in 0 until backoff())
    }

    /** An automatic request is sent. */
    fun sent(now: Long) { sent.addLast(now) }

    fun succeeded() { failures = 0 }

    fun failed(now: Long) { failures++; failedAt = now }

    private fun backoff() = minOf(RETRY_MS shl minOf(failures - 1, 6), MAX_RETRY_MS)

    companion object {
        const val MAX_PER_DAY = 8
        private const val RETRY_MS = 5 * 60_000L
        private const val MAX_RETRY_MS = 3 * 60 * 60_000L
        private const val DAY = 24 * 60 * 60_000L
    }
}

/**
 * Sky from Open-Meteo's WMO weather codes: Material Symbols glyph for the Home line, colored Meteocons picture for
 * the panel.
 */
enum class Sky(@param:StringRes val label: Int, @param:DrawableRes private val day: Int, @param:DrawableRes private val night: Int = day) {
    CLEAR(R.string.sky_clear, R.drawable.ic_sunny, R.drawable.ic_clear_night),
    PARTLY_CLOUDY(R.string.sky_partly_cloudy, R.drawable.ic_partly_cloudy_day, R.drawable.ic_partly_cloudy_night),
    CLOUDY(R.string.sky_cloudy, R.drawable.ic_cloud),
    FOG(R.string.sky_fog, R.drawable.ic_foggy),
    DRIZZLE(R.string.sky_drizzle, R.drawable.ic_rainy),
    RAIN(R.string.sky_rain, R.drawable.ic_rainy),
    FREEZING_RAIN(R.string.sky_freezing_rain, R.drawable.ic_weather_mix),
    SNOW(R.string.sky_snow, R.drawable.ic_weather_snowy),
    THUNDERSTORM(R.string.sky_thunderstorm, R.drawable.ic_thunderstorm),
    UNKNOWN(R.string.sky_unknown, R.drawable.ic_cloud);

    @DrawableRes
    fun icon(isDay: Boolean) = if (isDay) day else night

    /** Panel picture; drizzle has its own, distinct from rain, and freezing rain uses the sleet one. */
    @DrawableRes
    fun picture(isDay: Boolean) = when (this) {
        CLEAR -> if (isDay) R.drawable.weather_clear_day else R.drawable.weather_clear_night
        PARTLY_CLOUDY -> if (isDay) R.drawable.weather_partly_cloudy_day else R.drawable.weather_partly_cloudy_night
        CLOUDY, UNKNOWN -> R.drawable.weather_cloudy
        FOG -> R.drawable.weather_fog
        DRIZZLE -> R.drawable.weather_drizzle
        RAIN -> R.drawable.weather_rain
        FREEZING_RAIN -> R.drawable.weather_sleet
        SNOW -> R.drawable.weather_snow
        THUNDERSTORM -> R.drawable.weather_thunderstorms
    }

    companion object {
        fun of(code: Int) = when (code) {
            0 -> CLEAR
            1, 2 -> PARTLY_CLOUDY
            3 -> CLOUDY
            45, 48 -> FOG
            51, 53, 55 -> DRIZZLE
            56, 57, 66, 67 -> FREEZING_RAIN
            61, 63, 65, 80, 81, 82 -> RAIN
            71, 73, 75, 77, 85, 86 -> SNOW
            95, 96, 99 -> THUNDERSTORM
            else -> UNKNOWN
        }
    }
}

enum class TemperatureUnit {
    CELSIUS,
    FAHRENHEIT;

    fun format(celsius: Double): String {
        val value = if (this == FAHRENHEIT) celsius * 9 / 5 + 32 else celsius
        return "${value.roundToInt()}°"
    }

    companion object {
        /** Countries where CLDR 49 prefers °F for weather (`unitPreferenceData`, usage `weather`). */
        private val fahrenheitCountries = setOf("BS", "BZ", "KY", "PR", "PW", "US")

        /**
         * [preference]: value of the system locale's `mu` extension ("celsius", "fahrenhe", "kelvin"), null without a
         * regional preference; otherwise, the locale's country. Kelvin: °C.
         */
        fun of(preference: String?, country: String) = when (preference) {
            "fahrenhe" -> FAHRENHEIT
            "celsius", "kelvin" -> CELSIUS
            else -> if (country.uppercase() in fahrenheitCountries) FAHRENHEIT else CELSIUS
        }
    }
}
