package io.github.danyk20.cardholder.core.testing.repository

import io.github.danyk20.cardholder.core.domain.location.Place
import io.github.danyk20.cardholder.core.domain.location.Surroundings
import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.domain.repository.PlaceRepository
import io.github.danyk20.cardholder.core.model.CardId
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [PlaceRepository]. Visits are only recorded, not clustered; tests add the places they
 * need with [add]. Cards with suggestions switched off are left out like in the real repository.
 */
class FakePlaceRepository(private val cards: CardRepository) : PlaceRepository {
    val places = MutableStateFlow<List<Place>>(emptyList())

    /** Every recorded visit, in order. */
    val visits = mutableListOf<Pair<CardId, Surroundings>>()

    var now: Instant = Instant.parse("2026-06-01T12:00:00Z")

    fun add(place: Place) = places.update { it + place }

    override suspend fun suggestionPlaces(): List<Place> {
        val switchedOn = cards.observeCards().first().filter { it.suggestByLocation }.map { it.id }.toSet()
        return places.value.filter { it.cardId in switchedOn }
    }

    override fun observeGeofencePlaces(limit: Int): Flow<List<Place>> =
        combine(places, cards.observeCards()) { places, cards ->
            val switchedOn = cards.filter { it.suggestByLocation }.map { it.id }.toSet()
            places.filter { it.cardId in switchedOn && it.centre != null }
                .sortedWith(compareByDescending<Place> { it.useCount }.thenByDescending { it.lastUsedAt })
                .take(limit)
        }

    override suspend fun places(ids: Collection<Long>): List<Place> = places.value.filter { it.id in ids }

    override fun observeCount(cardId: CardId): Flow<Int> = places.map { all -> all.count { it.cardId == cardId } }

    override fun observeTotal(): Flow<Int> = places.map { it.size }

    override suspend fun recordVisit(cardId: CardId, surroundings: Surroundings): Boolean {
        visits += cardId to surroundings
        return surroundings.fix != null || surroundings.accessPoints.isNotEmpty()
    }

    override suspend fun markNotified(placeId: Long) = places.update { all ->
        all.map { if (it.id == placeId) it.copy(lastNotifiedAt = now) else it }
    }

    override suspend fun lastNotifiedAt(cardId: CardId?): Instant? =
        places.value.filter { cardId == null || it.cardId == cardId }.mapNotNull { it.lastNotifiedAt }.maxOrNull()

    override suspend fun forget(placeId: Long) = places.update { all -> all.filterNot { it.id == placeId } }

    override suspend fun forgetCard(cardId: CardId) = places.update { all -> all.filterNot { it.cardId == cardId } }

    override suspend fun forgetAll() = places.update { emptyList() }
}
