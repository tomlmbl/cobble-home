package io.github.tomlmbl.launcher.weather

/**
 * Last weather received, reduced to the displayed fields. [cityId]: requested city, so that the weather of a former
 * city is never shown. "Now", high and low are read at display time from [hours] and [days]: the forecast covers
 * the hours between two requests.
 */
data class Forecast(
    val fetchedAt: Long,
    val cityId: Long,
    val hours: List<Hour>,
    val days: List<Day> = emptyList(),
) {
    fun now(at: Long) = Weather.hourAt(hours, at)
    fun day(at: Long) = Weather.dayAt(days, at)
    fun rainFrom(at: Long) = Weather.rainFrom(hours, day(at), days, at)
}

/** City source: none (weather off), chosen, or nearest to the last known position. */
enum class WeatherMode { OFF, CITY, LOCATION }

/** Weather setting and last value, kept in a private file excluded from backup. */
data class WeatherData(
    val mode: WeatherMode = WeatherMode.OFF,
    val city: City? = null,
    val forecast: Forecast? = null,
) {
    /** Weather of the current city only (without a city, weather is off). */
    val current get() = forecast?.takeIf { it.cityId == city?.id }

    fun choose(city: City) = copy(mode = WeatherMode.CITY, city = city)

    /** City found from the position; the weather of another city stays until the next one, without being shown. */
    fun located(city: City) = copy(mode = WeatherMode.LOCATION, city = city)

    fun off() = WeatherData()
}

/** File format: one line per item, tab-separated fields; unreadable lines are ignored. */
object WeatherFormat {
    private const val HEADER = "launcher-weather\t2"

    fun encode(data: WeatherData): String = buildString {
        append(HEADER).append('\n')
        append("mode\t").append(data.mode.name).append('\n')
        data.city?.let { append("city\t").append(Cities.encode(it)).append('\n') }
        data.forecast?.let { f ->
            append("fetched\t").append(f.fetchedAt).append('\t').append(f.cityId).append('\n')
            f.days.forEach { append("day\t").append(it.time).append('\t').append(it.high).append('\t').append(it.low).append('\n') }
            f.hours.forEach { append("hour\t").append(hour(it)).append('\n') }
        }
    }

    fun decode(text: String): WeatherData {
        val lines = text.lineSequence().map { it.split('\t') }.toList()
        val city = lines.lastOrNull { it[0] == "city" }?.drop(1)?.joinToString("\t")?.let(Cities::decode)
        val mode = WeatherMode.entries.find { it.name == lines.lastOrNull { l -> l[0] == "mode" }?.getOrNull(1) }
            ?.takeIf { city != null || it == WeatherMode.OFF } ?: WeatherMode.OFF
        // Version 1: "now" and "day" lines without a date, ignored; the hours stay until the next request.
        val fetched = lines.lastOrNull { it[0] == "fetched" }
        val forecast = if (fetched != null) {
            val at = fetched.getOrNull(1)?.toLongOrNull()
            val id = fetched.getOrNull(2)?.toLongOrNull()
            if (at == null || id == null) null else Forecast(
                fetchedAt = at,
                cityId = id,
                hours = lines.filter { it[0] == "hour" }.mapNotNull { hour(it, 1) },
                days = lines.filter { it[0] == "day" }.mapNotNull(::day),
            )
        } else null
        return WeatherData(mode, city.takeIf { mode != WeatherMode.OFF }, forecast)
    }

    private fun day(fields: List<String>): Day? {
        val time = fields.getOrNull(1)?.toLongOrNull() ?: return null
        val high = fields.getOrNull(2)?.toDoubleOrNull() ?: return null
        val low = fields.getOrNull(3)?.toDoubleOrNull() ?: return null
        return Day(time, high, low)
    }

    /** Rain probability as the last field, missing from older files: read as null. */
    private fun hour(h: Hour) = "${h.time}\t${h.temperature}\t${h.code}\t${if (h.isDay) 1 else 0}" + (h.rain?.let { "\t$it" } ?: "")

    private fun hour(fields: List<String>, from: Int): Hour? {
        val time = fields.getOrNull(from)?.toLongOrNull() ?: return null
        val temperature = fields.getOrNull(from + 1)?.toDoubleOrNull() ?: return null
        val code = fields.getOrNull(from + 2)?.toIntOrNull() ?: return null
        return Hour(time, temperature, code, fields.getOrNull(from + 3) == "1", fields.getOrNull(from + 4)?.toIntOrNull())
    }
}
