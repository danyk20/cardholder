package io.github.danyk20.cardholder.core.domain.repository

import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.model.CountryCode

interface CountryRepository {
    /** All countries sorted by their localized name. */
    fun countries(): List<Country>

    fun country(code: CountryCode): Country?
}
