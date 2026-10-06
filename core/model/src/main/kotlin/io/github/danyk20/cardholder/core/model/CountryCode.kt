package io.github.danyk20.cardholder.core.model

/** ISO 3166-1 alpha-2 country code, e.g. `CH`. */
@JvmInline
value class CountryCode private constructor(val value: String) {
    /** Flag emoji built from regional indicator symbols. */
    val flagEmoji: String
        get() = value.map { Character.toChars(REGIONAL_INDICATOR_A + (it - 'A')).concatToString() }.joinToString("")

    companion object {
        private const val REGIONAL_INDICATOR_A = 0x1F1E6

        /** Returns a [CountryCode] for a two-letter code (case-insensitive), or `null` if malformed. */
        fun of(code: String): CountryCode? {
            val normalized = code.trim().uppercase()
            return if (normalized.length == 2 && normalized.all { it in 'A'..'Z' }) CountryCode(normalized) else null
        }
    }
}
