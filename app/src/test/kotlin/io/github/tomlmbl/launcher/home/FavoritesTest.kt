package io.github.tomlmbl.launcher.home

import io.github.tomlmbl.launcher.settings.ClockFamily
import io.github.tomlmbl.launcher.settings.ClockFont
import io.github.tomlmbl.launcher.settings.ClockWeight
import io.github.tomlmbl.launcher.settings.Config
import io.github.tomlmbl.launcher.settings.ConfigFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesTest {

    private fun fav(id: String) = Favorite(id, "Label $id")
    private fun favorites(vararg ids: String) = Favorites(ids.map(::fav))
    private fun Favorites.ids() = items.map { it.appId }

    @Test
    fun addGoesToTheEndAndIgnoresDuplicates() {
        assertEquals(listOf("a", "b", "c"), favorites("a", "b").add(fav("c")).ids())
        assertEquals(listOf("a", "b"), favorites("a", "b").add(fav("a")).ids())
    }

    @Test
    fun fullListRefusesAddAndInsert() {
        val full = Favorites((1..MAX_FAVORITES).map { fav("f$it") })
        assertEquals(full, full.add(fav("x")))
        assertEquals(full, full.insert(0, fav("x")))
    }

    @Test
    fun longerSavedListIsCutToTheFirstEight() {
        val saved = Config(favorites = Favorites((1..12).map { fav("f$it") }))
        val decoded = ConfigFormat.decode(ConfigFormat.encode(saved))
        assertEquals((1..MAX_FAVORITES).map { "f$it" }, decoded.favorites.ids())
    }

    @Test
    fun removeKeepsTheOrderOfTheOthers() {
        assertEquals(listOf("a", "c"), favorites("a", "b", "c").remove("b").ids())
    }

    @Test
    fun replaceKeepsThePosition() {
        assertEquals(listOf("a", "x", "c"), favorites("a", "b", "c").replace("b", fav("x")).ids())
    }

    @Test
    fun replaceByAnotherFavoriteChangesNothing() {
        assertEquals(listOf("a", "b", "c"), favorites("a", "b", "c").replace("b", fav("c")).ids())
    }

    @Test
    fun moveReordersOnlyTheMovedItem() {
        assertEquals(listOf("b", "c", "a", "d"), favorites("a", "b", "c", "d").move(0, 2).ids())
        assertEquals(listOf("d", "a", "b", "c"), favorites("a", "b", "c", "d").move(3, 0).ids())
        assertEquals(listOf("a", "b"), favorites("a", "b").move(0, 5).ids())
    }

    @Test
    fun uninstalledAppKeepsItsPlaceAndComesBackWhenReinstalled() {
        val list = favorites("a", "b", "c")
        val uninstalled = list.resolve(mapOf("a" to 1, "c" to 3))
        assertEquals(listOf("a", "b", "c"), uninstalled.map { it.favorite.appId })
        assertEquals(FavoriteSlot.Missing(fav("b")), uninstalled[1])

        val reinstalled = list.resolve(mapOf("a" to 1, "b" to 2, "c" to 3))
        assertEquals(FavoriteSlot.Installed(fav("b"), 2), reinstalled[1])
    }

    @Test
    fun formatRoundTripKeepsOrderAndLabels() {
        val config = Config(Favorites(listOf(Favorite("0|a/.A", "Été"), Favorite("10|b/.B", "Tab\there"))))
        val decoded = ConfigFormat.decode(ConfigFormat.encode(config))
        assertEquals(listOf("0|a/.A", "10|b/.B"), decoded.favorites.ids())
        assertEquals(listOf("Été", "Tab here"), decoded.favorites.items.map { it.label })
    }

    @Test
    fun formatIgnoresUnknownAndBrokenLines() {
        val text = "launcher-config\t2\nfav\ta\tA\ndock\tx\nfav\t\tEmpty\nfav\tb\nfav\ta\tAgain\n"
        assertEquals(listOf("a", "b"), ConfigFormat.decode(text).favorites.ids())
    }

    @Test
    fun emptyFileGivesNoFavorites() {
        assertEquals(Config(), ConfigFormat.decode(""))
    }

    @Test
    fun clockFontRoundTripsAndFallsBackOnUnknownValues() {
        val config = Config(clockFont = ClockFont(ClockFamily.MONO, ClockWeight.LIGHT))
        assertEquals(config, ConfigFormat.decode(ConfigFormat.encode(config)))
        assertEquals(ClockFont(ClockFamily.SERIF, ClockWeight.REGULAR), ConfigFormat.decode("clock\tSERIF\tHEAVY\n").clockFont)
    }
}
