package io.github.danyk20.cardholder.feature.cardeditor

import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.model.CardValidity
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.model.Region

// The rules for choosing the countries a card is valid in, kept free of UI so they can be unit-tested.

/** The countries [CardValidity] lists; empty when valid everywhere. */
internal fun CardValidity.selectedCountries(): Set<CountryCode> = when (this) {
    CardValidity.Everywhere -> emptySet()
    is CardValidity.Countries -> countries
}

/** Whether every member of [region] is selected, which is when its shortcut chip shows as selected. */
internal fun regionSelected(selected: Set<CountryCode>, region: Region): Boolean =
    selected.containsAll(region.countries)

/** Adds all members of [region], or removes them all when every member is already selected. */
internal fun toggleRegion(selected: Set<CountryCode>, region: Region): Set<CountryCode> =
    if (regionSelected(selected, region)) selected - region.countries else selected + region.countries

/** The validity to store: everywhere when all countries are chosen or nothing is selected. */
internal fun resolveValidity(allCountries: Boolean, selected: Set<CountryCode>): CardValidity =
    if (allCountries) CardValidity.Everywhere else CardValidity.of(selected)

/**
 * The countries to offer in the picker: the [catalogue] plus any selected code it lacks (such as `XK`
 * from an import), so every selected country is visible and can be unticked.
 */
internal fun countryOptions(catalogue: List<Country>, selected: Set<CountryCode>): List<Country> {
    val known = catalogue.mapTo(mutableSetOf()) { it.code }
    return catalogue + selected.filter { it !in known }.map { Country(code = it, name = it.value) }
}
