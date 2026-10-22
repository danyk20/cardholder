package io.github.danyk20.cardholder.core.model

/**
 * Where a card is valid: [Everywhere] (the default) or only in a set of countries.
 *
 * Like the title, this is shown without unlocking the card, so it is treated as non-sensitive.
 */
sealed interface CardValidity {
    /** Valid in every country. */
    data object Everywhere : CardValidity

    /** Valid only in [countries]. Never empty: an empty selection is [Everywhere], see [of]. */
    data class Countries(val countries: Set<CountryCode>) : CardValidity {
        init {
            require(countries.isNotEmpty()) { "An empty selection is Everywhere, use CardValidity.of()" }
        }
    }

    /**
     * Whether the card counts in [country]. An unknown country (`null`) counts everything as valid,
     * so cards are never hidden on uncertainty.
     */
    fun isValidIn(country: CountryCode?): Boolean = when (this) {
        Everywhere -> true
        is Countries -> country == null || country in countries
    }

    companion object {
        /** Builds the validity for a selection; an empty selection means [Everywhere]. */
        fun of(countries: Collection<CountryCode>): CardValidity =
            if (countries.isEmpty()) Everywhere else Countries(countries.toSet())
    }
}
