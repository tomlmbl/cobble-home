package io.github.tomlmbl.launcher.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Search in the bundled list. Sample places, except Open-Meteo's example coordinates. */
class CitiesTest {

    private val cities = listOf(
        City(1, "Bergeville", "Bergeville", "AA", "North", 52.52, 13.41, 20_000),
        City(2, "Berg", "Berg", "BB", "", 10.0, 10.0, 16_000),
        City(3, "Bérgamo Nuevo", "Bergamo Nuevo", "CC", "West", 1.0, 1.0, 900_000),
        City(4, "Søberg", "Soberg", "DD", "", 2.0, 2.0, 50_000),
        City(5, "Ostberg", "Ostberg", "EE", "", 3.0, 3.0, 2_000_000),
    )

    @Test
    fun fewerThanTwoCharactersFindNothing() {
        assertTrue(CityIndex(cities).search("b").isEmpty())
        assertTrue(CityIndex(cities).search("  ").isEmpty())
    }

    @Test
    fun exactNameFirstThenPrefixesByPopulation() {
        assertEquals(listOf(2L, 3L, 1L), CityIndex(cities).search("berg").map { it.id })
    }

    @Test
    fun accentsAndCaseAreIgnored() {
        assertEquals(listOf(3L), CityIndex(cities).search("BERGAMO").map { it.id })
        assertEquals(listOf(4L), CityIndex(cities).search("sø").map { it.id })
        assertEquals(listOf(4L), CityIndex(cities).search("sob").map { it.id })
    }

    @Test
    fun onlyPrefixesMatch() {
        assertTrue(CityIndex(cities).search("tberg").isEmpty())
    }

    @Test
    fun onlyTheBestAreKeptWhateverTheOrderOfTheList() {
        assertEquals(listOf(2L, 3L, 1L), CityIndex(cities.reversed()).search("berg").map { it.id })
        assertEquals(listOf(2L, 3L), CityIndex(cities.shuffled(java.util.Random(4))).search("berg", limit = 2).map { it.id })
    }

    @Test
    fun resultsAreLimited() {
        assertEquals(2, CityIndex(cities).search("be", limit = 2).size)
    }

    @Test
    fun onlineResultsAlreadyInTheListAreNotRepeated() {
        val online = listOf(cities[0], City(9, "Bergheim", "Bergheim", "FF", "", 0.0, 0.0, 0))
        assertEquals(listOf(9L), Cities.withoutShown(online, shown = cities.take(2)).map { it.id })
    }

    @Test
    fun linesRoundTrip() {
        val line = Cities.encode(cities[2])
        assertEquals(cities[2], Cities.decode(line))
        assertEquals(cities[0].copy(ascii = "Bergeville"), Cities.decode(Cities.encode(cities[0])))
    }

    @Test
    fun unreadableLinesAreSkipped() {
        assertNull(Cities.decode("garbage"))
        assertNull(Cities.decode("x\tBerg\t\tBB\t\tnot-a-number\t10\t1"))
    }

    @Test
    fun nearestCityToAPosition() {
        assertEquals(1L, Cities.nearest(cities.asSequence(), 52.6, 13.3)?.id)
        assertEquals(5L, Cities.nearest(cities.asSequence(), 3.2, 2.9)?.id)
        assertNull(Cities.nearest(emptySequence(), 0.0, 0.0))
    }

    @Test
    fun nearestAcrossTheDateLine() {
        val places = sequenceOf(
            City(1, "East", "East", "AA", "", 0.0, 179.5, 1),
            City(2, "Middle", "Middle", "BB", "", 0.0, 170.0, 1),
        )
        assertEquals(1L, Cities.nearest(places, 0.0, -179.8)?.id)
    }
}
