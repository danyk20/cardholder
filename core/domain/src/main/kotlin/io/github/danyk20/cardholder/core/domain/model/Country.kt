package io.github.danyk20.cardholder.core.domain.model

import io.github.danyk20.cardholder.core.model.CountryCode

/** A country with its name localized for the current user. */
data class Country(
    val code: CountryCode,
    val name: String,
)
