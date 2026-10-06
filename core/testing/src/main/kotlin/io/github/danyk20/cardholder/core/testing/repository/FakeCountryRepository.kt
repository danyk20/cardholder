package io.github.danyk20.cardholder.core.testing.repository

import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.domain.repository.CountryRepository
import io.github.danyk20.cardholder.core.model.CountryCode

class FakeCountryRepository : CountryRepository {
    private val countries = listOf("AT" to "Austria", "CH" to "Switzerland", "SK" to "Slovakia")
        .map { (code, name) -> Country(CountryCode.of(code)!!, name) }

    override fun countries(): List<Country> = countries

    override fun country(code: CountryCode): Country? = countries.firstOrNull { it.code == code }
}
