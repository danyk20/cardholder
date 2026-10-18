package io.github.danyk20.cardholder.core.domain.repository

import io.github.danyk20.cardholder.core.domain.location.Place
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

    fun observeCount(cardId: CardId): Flow<Int>

    fun observeTotal(): Flow<Int>

    /** Learns that [cardId] is being used in [surroundings]; `false` if they held nothing worth keeping. */
    suspend fun recordVisit(cardId: CardId, surroundings: Surroundings): Boolean

    suspend fun markNotified(placeId: Long)

    /** When the user was last notified about a place of [cardId], or of any card if `null`. */
    suspend fun lastNotifiedAt(cardId: CardId? = null): Instant?

    suspend fun forget(placeId: Long)

    suspend fun forgetCard(cardId: CardId)

    suspend fun forgetAll()
}
