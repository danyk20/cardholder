package io.github.danyk20.cardholder.core.domain.repository

import io.github.danyk20.cardholder.core.domain.country.CountryState
import io.github.danyk20.cardholder.core.model.CountryCode
import kotlinx.coroutines.flow.Flow

/** The country the phone is in, and the user's choice of country. */
interface CurrentCountryRepository {
    /** The stored state; available at once, even before the first [refresh] of a launch. */
    val state: Flow<CountryState>

    /** Detects the country again. Cheap enough to call whenever the card list is shown. */
    suspend fun refresh()

    /** Uses [code] instead of the detected country until detection changes. */
    suspend fun setOverride(code: CountryCode)

    /** Goes back to the detected country. */
    suspend fun clearOverride()
}
