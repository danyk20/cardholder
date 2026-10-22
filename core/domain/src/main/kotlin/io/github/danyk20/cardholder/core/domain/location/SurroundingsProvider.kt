package io.github.danyk20.cardholder.core.domain.location

import java.time.Duration

/** Reads the phone's Wi‑Fi scan results and location, each only when asked and as cheaply as possible. */
interface SurroundingsProvider {
    /**
     * Access points around: the results of the last system scan if it's no older than [maxAge],
     * otherwise of one new scan. Empty without precise location permission or with Location off.
     */
    suspend fun accessPoints(maxAge: Duration): List<AccessPoint>

    /**
     * The last known location if it's recent and accurate enough for [request], otherwise one new fix.
     * `null` if none arrives in time, without permission or with Location off.
     */
    suspend fun locationFix(request: FixRequest): LocationFix?
}

/** How a location fix is obtained. */
data class FixRequest(
    val precision: FixPrecision,
    /** An older last known location isn't reused. */
    val maxAge: Duration,
    /** A last known location at least this accurate is reused. */
    val goodEnoughMeters: Float,
    /** The longest wait for a new fix before giving up. */
    val timeout: Duration,
) {
    companion object {
        /** Where a card is being used: worth a precise fix, as it's remembered. */
        val Learn = FixRequest(FixPrecision.HIGH, Duration.ofMinutes(2), 100f, Duration.ofSeconds(10))

        /** Which places are near: Wi‑Fi and mobile network positioning suffice for a kilometre. */
        val Nearby = FixRequest(FixPrecision.BALANCED, Duration.ofMinutes(2), 100f, Duration.ofSeconds(10))
    }
}

enum class FixPrecision {
    /** May use GPS. */
    HIGH,

    /** Wi‑Fi and mobile network positioning; uses much less power. */
    BALANCED,

    /** Mobile network positioning only, the cheapest and the coarsest; never GPS. */
    LOW,
}
