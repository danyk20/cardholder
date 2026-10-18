package io.github.danyk20.cardholder.core.domain.location

import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

/** Splits names of shops and Wi‑Fi networks into comparable words. */
internal object Words {
    private val Separators = Regex("[^\\p{L}\\p{N}]+")

    // Splits "MediaMarktGuest" into Media|Markt|Guest, "EDEKAGast" into EDEKA|Gast and "Lidl24" into Lidl|24.
    private val CaseChanges =
        Regex("(?<=\\p{Ll})(?=\\p{Lu})|(?<=\\p{Lu})(?=\\p{Lu}\\p{Ll})|(?<=\\p{L})(?=\\p{N})|(?<=\\p{N})(?=\\p{L})")
    private val Accents = Regex("\\p{Mn}+")
    private val GermanLetters = mapOf("ä" to "ae", "ö" to "oe", "ü" to "ue", "ß" to "ss")

    /** Words joined back together, e.g. "Media" + "Markt", are matched up to this many. */
    private const val MAX_JOINED = 3

    /** Lower case without accents: "Müller" becomes "muller". */
    fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Accents, "")

    /** German spelling without umlauts: "Müller" becomes "mueller". */
    fun transliterate(text: String): String = GermanLetters.entries.fold(text.lowercase(Locale.ROOT)) { acc, entry ->
        acc.replace(entry.key, entry.value)
    }

    /** The parts of [text] between spaces and punctuation. */
    fun segments(text: String): List<String> = text.split(Separators).filter { it.isNotEmpty() }

    /** The folded words of [text], also split where the case changes. */
    fun tokens(text: String): List<String> = segments(text).flatMap(::caseParts).map(::fold)

    /**
     * Every way a word may appear in a network name: each segment, each part split where the case
     * changes and short runs of those parts joined back together.
     */
    fun networkWords(ssid: String): Set<String> = buildSet {
        for (segment in segments(ssid)) {
            add(fold(segment))
            val parts = caseParts(segment)
            for (start in parts.indices) {
                for (end in start + 1..min(parts.size, start + MAX_JOINED)) {
                    add(fold(parts.subList(start, end).joinToString("")))
                }
            }
        }
    }

    private fun caseParts(segment: String): List<String> = segment.split(CaseChanges).filter { it.isNotEmpty() }
}
