package io.github.danyk20.cardholder.core.model

/**
 * Groups of countries offered as shortcuts when choosing where a card is valid.
 *
 * Membership as of 2026-10-22. Update the members here, and the expected counts in `RegionTest`,
 * when a country joins or leaves a group.
 */
enum class Region(val countries: Set<CountryCode>) {
    /** The 27 members of the European Union. */
    EU(
        codes(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", "IT", "LV", "LT",
            "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE",
        ),
    ),

    /** The 29 countries of the Schengen Area: the EU without CY and IE, plus IS, LI, NO and CH. */
    SCHENGEN(
        codes(
            "AT", "BE", "BG", "HR", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IT", "LV", "LT", "LU", "MT",
            "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE", "IS", "LI", "NO", "CH",
        ),
    ),

    /** The 21 EU countries using the euro (Bulgaria joined on 2026-01-01). */
    EUROZONE(
        codes(
            "AT", "BE", "BG", "HR", "CY", "EE", "FI", "FR", "DE", "GR", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
            "PT", "SK", "SI", "ES",
        ),
    ),

    /** The 30 countries of the European Economic Area: the EU plus IS, LI and NO. */
    EEA(
        codes(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", "IT", "LV", "LT",
            "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE", "IS", "LI", "NO",
        ),
    ),
}

private fun codes(vararg values: String): Set<CountryCode> = values.mapTo(LinkedHashSet()) {
    requireNotNull(CountryCode.of(it)) { "Invalid country code: $it" }
}
