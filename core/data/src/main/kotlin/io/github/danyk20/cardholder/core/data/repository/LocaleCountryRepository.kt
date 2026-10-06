package io.github.danyk20.cardholder.core.data.repository

import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.domain.repository.CountryRepository
import io.github.danyk20.cardholder.core.model.CountryCode
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

/** Countries from the platform's ISO 3166 list, named in the user's current language. */
internal class LocaleCountryRepository @Inject constructor() : CountryRepository {
    override fun countries(): List<Country> {
        val locale = Locale.getDefault()
        val collator = Collator.getInstance(locale)
        return Locale.getISOCountries()
            .mapNotNull { code -> CountryCode.of(code)?.let { Country(it, displayName(it, locale)) } }
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    override fun country(code: CountryCode): Country = Country(code, displayName(code, Locale.getDefault()))

    private fun displayName(code: CountryCode, locale: Locale): String =
        Locale.Builder().setRegion(code.value).build().getDisplayCountry(locale).ifBlank { code.value }
}
