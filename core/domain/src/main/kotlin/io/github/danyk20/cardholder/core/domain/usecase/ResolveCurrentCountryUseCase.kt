package io.github.danyk20.cardholder.core.domain.usecase

import io.github.danyk20.cardholder.core.domain.country.ApproximateLocationSource
import io.github.danyk20.cardholder.core.domain.country.CountryBorders
import io.github.danyk20.cardholder.core.domain.country.CountrySource
import io.github.danyk20.cardholder.core.domain.country.CurrentCountry
import io.github.danyk20.cardholder.core.domain.country.NetworkCountrySource
import io.github.danyk20.cardholder.core.domain.country.StoredCountry
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Works out the country the phone is in, from the cheapest source first:
 * 1. the country of the mobile network, which needs no permission and no battery;
 * 2. the approximate location, only if location is already permitted, mapped to borders offline;
 * 3. the country detected before, however old.
 * The country is unknown only when nothing was ever detected.
 *
 * A manual choice is dropped once a fresh detection differs from the country it was made against.
 * Cached or unknown results never drop it.
 */
@Singleton
class ResolveCurrentCountryUseCase @Inject constructor(
    private val network: NetworkCountrySource,
    private val location: ApproximateLocationSource,
    private val borders: CountryBorders,
    private val clock: Clock,
) {
    /** In memory only: after a restart the first lookup is allowed again. */
    private var lastLocationLookup: Instant? = null

    suspend operator fun invoke(stored: StoredCountry): StoredCountry {
        val fresh = freshDetection()
        val detected = fresh ?: stored.detected?.copy(source = CountrySource.LAST_KNOWN)
        val keepOverride = fresh == null || stored.override == null || fresh.code == stored.overrideBasis
        return if (keepOverride) {
            StoredCountry(detected, stored.override, stored.overrideBasis)
        } else {
            StoredCountry(detected)
        }
    }

    /** The country read now, from the network or else from location; `null` if neither gives one. */
    private suspend fun freshDetection(): CurrentCountry? =
        network.networkCountry()?.let { CurrentCountry(it, CountrySource.NETWORK) } ?: locationDetection()

    private suspend fun locationDetection(): CurrentCountry? {
        if (!mayLookUpLocation()) return null
        val point = location.approximateLocation(LOCATION_MAX_AGE) ?: return null
        return borders.countryAt(point)?.let { CurrentCountry(it, CountrySource.LOCATION) }
    }

    /** Looks up the location at most once per [LOCATION_INTERVAL], counting a lookup that finds nothing too. */
    private fun mayLookUpLocation(): Boolean {
        val now = clock.instant()
        val last = lastLocationLookup
        if (last != null && Duration.between(last, now) < LOCATION_INTERVAL) return false
        lastLocationLookup = now
        return true
    }

    companion object {
        /** A last known location older than this is not used for the country. */
        val LOCATION_MAX_AGE: Duration = Duration.ofHours(6)

        /** Shortest time between two location lookups, to save battery when the list is opened often. */
        val LOCATION_INTERVAL: Duration = Duration.ofMinutes(30)
    }
}
