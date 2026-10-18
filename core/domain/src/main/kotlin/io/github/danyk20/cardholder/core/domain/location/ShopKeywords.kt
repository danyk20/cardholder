package io.github.danyk20.cardholder.core.domain.location

/** Finds a card's shop in Wi‑Fi network names, e.g. "Kaufland-Free" for the "Kaufland Card". */
object ShopKeywords {
    /** Shorter words of a shop's name match too many network names; catalogue entries can still add them. */
    private const val MIN_LENGTH = 3

    /** Words in loyalty programme names that don't name the shop. */
    private val StopWords = setOf(
        "card", "cards", "karte", "carte", "tarjeta", "plus", "club", "clubcard", "family", "rewards", "reward",
        "bonus", "points", "punkte", "loyalty", "member", "members", "extra", "extracare", "supercard", "advantage",
        "markt", "market", "supermarket", "supermarkt", "drogerie", "store", "stores", "shop", "sport", "sports",
        "beauty", "wifi", "wlan", "free", "guest", "gast", "the", "and", "und", "app", "my", "mein",
    )

    /** Keywords for a shop called [shopName], plus the catalogue's extra [wifiNames] for it. */
    fun of(shopName: String, wifiNames: Set<String> = emptySet()): Set<String> = buildSet {
        val segments = Words.segments(shopName)
        val joined = segments.takeIf { it.size > 1 }?.joinToString("")
        (segments + listOfNotNull(joined))
            .filter { it.length >= MIN_LENGTH && Words.fold(it) !in StopWords }
            .forEach { addSpellings(it) }
        wifiNames.forEach { addSpellings(it) }
    }

    /** Whether [ssid] contains one of [keywords] as a whole word ("dm" matches "dm-WLAN", not "admin"). */
    fun matches(ssid: String, keywords: Set<String>): Boolean =
        keywords.isNotEmpty() && Words.networkWords(ssid).any { it in keywords }

    private fun MutableSet<String>.addSpellings(word: String) {
        add(Words.fold(word))
        add(Words.fold(Words.transliterate(word)))
    }
}
