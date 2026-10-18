package io.github.danyk20.cardholder.core.domain.location

import io.github.danyk20.cardholder.core.model.CardId
import java.time.Instant

/**
 * Somewhere a card was used: the centre of its last location fixes (if any were accurate enough)
 * and the Wi‑Fi access points seen there.
 */
data class Place(
    val id: Long,
    val cardId: CardId,
    val centre: GeoPoint?,
    val accuracyMeters: Float?,
    val useCount: Int,
    val lastUsedAt: Instant,
    val lastNotifiedAt: Instant? = null,
    val accessPoints: List<AccessPoint> = emptyList(),
)

/** A card suggested because the user seems to be near where it's used. */
data class NearbyCard(val cardId: CardId, val reason: NearbyReason)

sealed interface NearbyReason {
    /** Access points of a place where the card was used are around; the higher [score], the closer. */
    data class WifiHere(val score: Double) : NearbyReason

    /** A network seen where the card was used in other branches of the same chain is around. */
    data class WifiChain(val rssi: Int) : NearbyReason

    /** A network named after the card's shop is around. */
    data class WifiName(val rssi: Int) : NearbyReason

    /** The nearest place where the card was used is [meters] away. */
    data class Distance(val meters: Double) : NearbyReason
}
