package io.github.danyk20.cardholder.core.domain.repository

import io.github.danyk20.cardholder.core.domain.location.Bssid
import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.domain.location.Place
import io.github.danyk20.cardholder.core.domain.location.StoredAccessPoint
import io.github.danyk20.cardholder.core.domain.location.Surroundings
import io.github.danyk20.cardholder.core.model.CardId
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Places where cards were used, kept only on this device. */
@Suppress("TooManyFunctions") // The single entry point to learned places.
interface PlaceRepository {
    /** The places of cards with suggestions switched on, with their access points. */
    suspend fun suggestionPlaces(): List<Place>

    /** Places with a known centre of cards with suggestions switched on, the most used first. */
    fun observeGeofencePlaces(limit: Int): Flow<List<Place>>

    suspend fun places(ids: Collection<Long>): List<Place>

    /** The places of [cardId], the most used first, with their fixes and access points. */
    fun observePlaces(cardId: CardId): Flow<List<Place>>

    fun observeCount(cardId: CardId): Flow<Int>

    fun observeTotal(): Flow<Int>

    /** Learns that [cardId] is being used in [surroundings]; `false` if they held nothing worth keeping. */
    suspend fun recordVisit(cardId: CardId, surroundings: Surroundings): Boolean

    suspend fun markNotified(placeId: Long)

    /** When the user was last notified about a place of [cardId], or of any card if `null`. */
    suspend fun lastNotifiedAt(cardId: CardId? = null): Instant?

    /**
     * Moves the place to [point] and pins it: its learned fixes are replaced by [point], and later
     * visits don't move it. Its Wi‑Fi access points are kept.
     *
     * @return `false` if the place no longer exists.
     */
    suspend fun movePlace(placeId: Long, point: GeoPoint): Boolean

    /** Adds a pinned place for [cardId] at [point] and returns its id. */
    suspend fun addPlace(cardId: CardId, point: GeoPoint): Long

    /** The Wi‑Fi access points of a place, removed ones too, the most seen first. */
    fun observeAccessPoints(placeId: Long): Flow<List<StoredAccessPoint>>

    /**
     * Removes [bssids] from a place, or restores them when [blocked] is `false`. A removed access point
     * no longer suggests the card and isn't learned at the place again.
     */
    suspend fun setAccessPointsBlocked(placeId: Long, bssids: Collection<Bssid>, blocked: Boolean)

    suspend fun forget(placeId: Long)

    suspend fun forgetCard(cardId: CardId)

    suspend fun forgetAll()
}
