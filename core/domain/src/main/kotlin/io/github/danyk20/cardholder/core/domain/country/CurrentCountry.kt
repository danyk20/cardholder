package io.github.danyk20.cardholder.core.domain.country

import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.model.CountryCode
import java.time.Duration

/** How the current country was found. */
enum class CountrySource {
    /** Chosen by the user. */
    MANUAL,

    /** The country the mobile network is registered in. */
    NETWORK,

    /** The country of an approximate location, mapped to borders offline. */
    LOCATION,

    /** Detected earlier and used again because nothing could be detected now. */
    LAST_KNOWN,
}

data class CurrentCountry(val code: CountryCode, val source: CountrySource)

/** The country the mobile network is registered in, or `null` when it is unknown. */
fun interface NetworkCountrySource {
    fun networkCountry(): CountryCode?
}

/** The phone's approximate location: the last known one if it is no older than [maxAge], else one new reading. */
interface ApproximateLocationSource {
    suspend fun approximateLocation(maxAge: Duration): GeoPoint?
}

/** Maps a point to the country it lies in, using data bundled with the app. */
interface CountryBorders {
    suspend fun countryAt(point: GeoPoint): CountryCode?
}

/** What the app remembers about the phone's country between launches. */
data class StoredCountry(
    /** The country last detected, with how it was found. */
    val detected: CurrentCountry? = null,
    /** The country the user picked instead of the detected one. */
    val override: CountryCode? = null,
    /** The detected country when [override] was picked, to notice when detection changes. */
    val overrideBasis: CountryCode? = null,
)

/** The country in use, with the detected one kept apart from a manual choice. */
data class CountryState(
    /** The country cards are filtered by: the manual choice if any, otherwise the detected one. */
    val effective: CurrentCountry?,
    /** The country detected automatically, even when a manual choice is in use. */
    val detected: CurrentCountry?,
) {
    val isManual: Boolean get() = effective?.source == CountrySource.MANUAL
}

fun StoredCountry.toState(): CountryState = CountryState(
    effective = override?.let { CurrentCountry(it, CountrySource.MANUAL) } ?: detected,
    detected = detected,
)
