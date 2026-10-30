package io.github.danyk20.cardholder.core.domain.location

import io.github.danyk20.cardholder.core.model.CardId
import java.time.Instant

/**
 * Somewhere a card was used: the centre of its last location fixes (if any were accurate enough)
 * and the Wi‑Fi access points seen there.
 *
 * @property blockedAccessPoints access points the user removed from the place: they say nothing about
 *   it, and visits don't add them back.
 * @property isPinned the user set the place's position on the map; later visits don't move it.
 * @property fixCount how many location fixes the centre is computed from, when they were loaded
 *   with the place.
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
    val blockedAccessPoints: List<AccessPoint> = emptyList(),
    val isPinned: Boolean = false,
    val createdAt: Instant = lastUsedAt,
    val fixCount: Int = 0,
)

/**
 * A Wi‑Fi access point stored at a place, as the user reviews it: how often and when it was seen, and
 * whether the user removed it.
 */
data class StoredAccessPoint(
    val accessPoint: AccessPoint,
    val seenCount: Int,
    val lastSeenAt: Instant,
    val isBlocked: Boolean,
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
