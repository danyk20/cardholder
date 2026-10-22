package io.github.danyk20.cardholder.core.testing.repository

import io.github.danyk20.cardholder.core.domain.country.CountrySource
import io.github.danyk20.cardholder.core.domain.country.CountryState
import io.github.danyk20.cardholder.core.domain.country.CurrentCountry
import io.github.danyk20.cardholder.core.domain.repository.CurrentCountryRepository
import io.github.danyk20.cardholder.core.model.CountryCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** [CurrentCountryRepository] holding a detected country and choice that tests set, counting refreshes. */
class FakeCurrentCountryRepository(detected: CountryCode? = null) : CurrentCountryRepository {
    private val stored = MutableStateFlow(
        CountryState(
            effective = detected?.let { CurrentCountry(it, CountrySource.NETWORK) },
            detected = detected?.let { CurrentCountry(it, CountrySource.NETWORK) },
        ),
    )

    override val state: Flow<CountryState> = stored

    var refreshes = 0
        private set

    /** Makes [code] the detected country, as a later detection would. */
    fun detect(code: CountryCode?) = stored.update { state ->
        val detected = code?.let { CurrentCountry(it, CountrySource.NETWORK) }
        CountryState(effective = if (state.isManual) state.effective else detected, detected = detected)
    }

    override suspend fun refresh() {
        refreshes++
    }

    override suspend fun setOverride(code: CountryCode) = stored.update {
        it.copy(effective = CurrentCountry(code, CountrySource.MANUAL))
    }

    override suspend fun clearOverride() = stored.update {
        it.copy(effective = it.detected)
    }
}
