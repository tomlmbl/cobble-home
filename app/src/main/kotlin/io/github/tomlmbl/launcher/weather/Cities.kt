package io.github.tomlmbl.launcher.weather

import io.github.tomlmbl.launcher.catalog.Alphabet

/**
 * A place from the bundled list (GeoNames) or from Open-Meteo geocoding, which shares its identifiers.
 * [country]: ISO 3166-1 code, its name is given by the platform in the device language; [region]: subdivision, in English.
 */
data class City(
    val id: Long,
    val name: String,
    val ascii: String,
    val country: String,
    val region: String,
    val latitude: Double,
    val longitude: Double,
    val population: Long,
)

/** List prepared for searching: names folded once, at load time, off the main thread. */
class CityIndex(val cities: List<City>) {

    private val keys = cities.map { listOf(Alphabet.fold(it.name), Alphabet.fold(it.ascii)) }

    /**
     * Prefix of the name or ASCII name, ignoring accents and case, at least 2 characters; exact name first, then
     * decreasing population. One pass, only the [limit] best are kept.
     */
    fun search(query: String, limit: Int = 4): List<City> {
        val q = Alphabet.fold(query.trim())
        if (q.length < 2) return emptyList()
        val order = compareBy<Pair<Boolean, City>> { !it.first }.thenByDescending { it.second.population }
        val best = ArrayList<Pair<Boolean, City>>(limit + 1)
        cities.forEachIndexed { i, city ->
            val names = keys[i]
            if (names.none { it.startsWith(q) }) return@forEachIndexed
            val candidate = (q in names) to city
            if (best.size == limit && order.compare(candidate, best.last()) >= 0) return@forEachIndexed
            val at = best.binarySearch(candidate, order).let { if (it < 0) -it - 1 else it }
            best.add(at, candidate)
            if (best.size > limit) best.removeAt(limit)
        }
        return best.map { it.second }
    }
}

object Cities {

    /** Online results without those already offered by the bundled list. */
    fun withoutShown(online: List<City>, shown: List<City>): List<City> {
        val ids = shown.mapTo(HashSet()) { it.id }
        return online.filter { it.id !in ids }
    }

    /** One line per place, tab-separated fields; ASCII name empty if identical to the name. */
    fun encode(city: City) = listOf(
        city.id, city.name, if (city.ascii == city.name) "" else city.ascii, city.country, city.region,
        city.latitude, city.longitude, city.population,
    ).joinToString("\t")

    /** Nearest city to a position, in one pass, with an approximate distance good enough at city scale. */
    fun nearest(cities: Sequence<City>, latitude: Double, longitude: Double): City? = cities.minByOrNull { city ->
        val dLat = city.latitude - latitude
        val dLon = (city.longitude - longitude).let { if (it > 180) it - 360 else if (it < -180) it + 360 else it } *
            Math.cos(Math.toRadians((city.latitude + latitude) / 2))
        dLat * dLat + dLon * dLon
    }

    /** Unreadable line: null, it is ignored. */
    fun decode(line: String): City? {
        val f = line.split('\t')
        if (f.size != 8) return null
        return City(
            id = f[0].toLongOrNull() ?: return null,
            name = f[1].ifEmpty { return null },
            ascii = f[2].ifEmpty { f[1] },
            country = f[3],
            region = f[4],
            latitude = f[5].toDoubleOrNull() ?: return null,
            longitude = f[6].toDoubleOrNull() ?: return null,
            population = f[7].toLongOrNull() ?: return null,
        )
    }
}
