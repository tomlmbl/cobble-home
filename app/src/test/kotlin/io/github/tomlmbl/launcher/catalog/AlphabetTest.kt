package io.github.tomlmbl.launcher.catalog

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AlphabetTest {

    private val alphabet = Alphabet(Locale.FRENCH)
    private fun sections(vararg labels: String) =
        alphabet.sections(labels.toList()) { it }.map { it.key to it.items }

    @Test
    fun accentedLabelsJoinTheirBaseLetter() {
        assertEquals(
            listOf("E" to listOf("Écran", "Email", "Été")),
            sections("Été", "Email", "Écran"),
        )
    }

    @Test
    fun turkishCaseDoesNotMoveLettersOutOfTheirSection() {
        // Under a Turkish locale, "I" does not become "ı"; searching "insta" finds "Instagram".
        val turkish = Alphabet(Locale.forLanguageTag("tr"))
        assertEquals(listOf("I"), turkish.sections(listOf("Instagram", "İzmir")) { it }.map { it.key })
        assertEquals(listOf("Instagram"), turkish.search(listOf("Instagram", "Maps"), "insta") { it })
        assertEquals(listOf("Instagram"), turkish.search(listOf("Instagram", "Maps"), "ınsta") { it })
    }

    @Test
    fun sortIgnoresCase() {
        assertEquals(listOf("apple", "Banque", "calendrier"), alphabet.sort(listOf("calendrier", "Banque", "apple")) { it })
    }

    @Test
    fun digitsSymbolsAndOtherScriptsGoToTheLeadingGroup() {
        assertEquals(
            listOf("#" to listOf("1Password", "Яндекс", "+Plus"), "A" to listOf("Agenda")).map { it.first },
            sections("Agenda", "1Password", "Яндекс", "+Plus").map { it.first },
        )
    }

    @Test
    fun blankLabelDoesNotCrash() {
        assertEquals(Alphabet.OTHER, alphabet.sectionKey("  "))
    }

    @Test
    fun searchIgnoresAccentsAndCase() {
        assertEquals(listOf("Météo"), alphabet.search(listOf("Météo", "Musique"), "METEO") { it })
    }

    @Test
    fun searchRanksWordStartsBeforeInnerMatches() {
        val labels = listOf("Calculatrice", "Bourse", "Paramètres", "Rappels")
        // "app": start of the word "Rappels"? No: an inner match. No word start here.
        assertEquals(listOf("Rappels"), alphabet.search(labels, "app") { it })
        // "ra": start of "Rappels", inside "Paramètres".
        assertEquals(listOf("Rappels", "Paramètres"), alphabet.search(labels, "ra") { it })
    }

    @Test
    fun searchRanksNameStartThenWordStartThenInner() {
        val labels = listOf("Webmail", "Google Maps", "Mail", "Cinéma")
        assertEquals(listOf("Mail", "Google Maps", "Cinéma", "Webmail"), alphabet.search(labels, "ma") { it })
    }

    @Test
    fun searchFindsWordStartAfterAnInnerMatch() {
        // "ma" first appears inside "Smart", then at the start of "Maps".
        assertEquals(listOf("Smart Maps", "Gamma"), alphabet.search(listOf("Gamma", "Smart Maps"), "ma") { it })
    }

    @Test
    fun railListsEveryLetterAfterTheLeadingGroup() {
        assertEquals(27, Alphabet.RAIL_KEYS.size)
        assertEquals(listOf("#", "A", "Z"), listOf(Alphabet.RAIL_KEYS.first(), Alphabet.RAIL_KEYS[1], Alphabet.RAIL_KEYS.last()))
    }

    @Test
    fun emptyQueryReturnsNothing() {
        assertEquals(emptyList<String>(), alphabet.search(listOf("Agenda"), "  ") { it })
    }
}
