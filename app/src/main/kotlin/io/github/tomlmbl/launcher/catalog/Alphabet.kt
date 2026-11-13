package io.github.tomlmbl.launcher.catalog

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** Section grouping the apps whose label starts with the same letter. */
data class Section<T>(val key: String, val items: List<T>)

/**
 * Catalog sorting, sections and search. Pure Kotlin, no Android API, so it can be tested off-device.
 * Sections A to Z after accent folding, with a leading "#" group for digits, symbols and other scripts.
 */
class Alphabet(private val locale: Locale) {

    private val collator: Collator = Collator.getInstance(locale)

    /** Label without accents or case, for comparing and searching. */
    fun fold(text: String): String = Companion.fold(text)

    fun sectionKey(label: String): String {
        val first = fold(label.trim()).firstOrNull() ?: return OTHER
        return if (first in 'a'..'z') first.uppercaseChar().toString() else OTHER
    }

    fun <T> sort(items: List<T>, label: (T) -> String): List<T> =
        items.sortedWith { a, b ->
            val sa = sectionKey(label(a))
            val sb = sectionKey(label(b))
            when {
                sa != sb -> sectionOrder(sa).compareTo(sectionOrder(sb))
                else -> collator.compare(label(a), label(b))
            }
        }

    fun <T> sections(items: List<T>, label: (T) -> String): List<Section<T>> =
        sort(items, label)
            .groupBy { sectionKey(label(it)) }
            .map { (key, group) -> Section(key, group) }

    /** Flat list: start of the name first, then start of another word, then substring; alphabetical within each rank. */
    fun <T> search(items: List<T>, query: String, label: (T) -> String): List<T> {
        val needle = fold(query.trim())
        if (needle.isEmpty()) return emptyList()
        return sort(items, label)
            .mapNotNull { item -> rank(fold(label(item)), needle)?.let { item to it } }
            .sortedBy { it.second }
            .map { it.first }
    }

    private fun rank(folded: String, needle: String): Int? {
        if (folded.startsWith(needle)) return 0
        var index = folded.indexOf(needle)
        if (index < 0) return null
        while (index >= 0) {
            if (!folded[index - 1].isLetterOrDigit()) return 1
            index = folded.indexOf(needle, index + 1)
        }
        return 2
    }

    private fun sectionOrder(key: String): Int = if (key == OTHER) -1 else key[0].code

    companion object {
        const val OTHER = "#"
        /** All rail keys, including those without an app. */
        val RAIL_KEYS: List<String> = listOf(OTHER) + ('A'..'Z').map { it.toString() }
        private val DIACRITICS = Regex("\\p{Mn}+")

        /**
         * Folding shared with the city search (weather/Cities.kt). Case folding is locale-independent ([Locale.ROOT])
         * and "ı" maps to "i": in Turkish or Azerbaijani, "I" must not become "ı", which is outside A to Z.
         */
        fun fold(text: String): String =
            DIACRITICS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT).replace('ı', 'i')
    }
}
