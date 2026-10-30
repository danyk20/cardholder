package io.github.danyk20.cardholder.core.domain.usecase

import io.github.danyk20.cardholder.core.domain.location.Bssid
import io.github.danyk20.cardholder.core.domain.location.GeoMath
import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.domain.location.Place
import io.github.danyk20.cardholder.core.domain.location.StoredAccessPoint
import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.domain.repository.PlaceRepository
import io.github.danyk20.cardholder.core.domain.repository.UserPreferencesRepository
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.PlaceSuggestions
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** How a card takes part in suggestions by location. */
data class CardPlacesState(
    /** Whether suggestions by location are on in the settings at all. */
    val isFeatureOn: Boolean,
    /** How many places the card was used at. */
    val placeCount: Int,
)

/** The place controls of one card: its suggestion switch, remembering and forgetting its places. */
class CardPlaces @Inject constructor(
    private val cardRepository: CardRepository,
    private val placeRepository: PlaceRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val rememberPlace: RememberPlaceUseCase,
    private val findNearbyCards: FindNearbyCardsUseCase,
) {
    fun observe(cardId: CardId): Flow<CardPlacesState> = combine(
        preferencesRepository.preferences,
        placeRepository.observeCount(cardId),
    ) { prefs, count -> CardPlacesState(prefs.placeSuggestions != PlaceSuggestions.OFF, count) }

    suspend fun setSuggestByLocation(cardId: CardId, enabled: Boolean) {
        cardRepository.setSuggestByLocation(cardId, enabled)
        findNearbyCards.invalidate()
    }

    /** Remembers the current place for the card, when the user says they're using it here. */
    suspend fun rememberHere(cardId: CardId): RememberResult = rememberPlace(cardId)

    suspend fun forget(cardId: CardId) {
        placeRepository.forgetCard(cardId)
        findNearbyCards.invalidate()
    }

    /** The places of [cardId] with their fixes and access points, the most used first. */
    fun observePlaces(cardId: CardId): Flow<List<Place>> = placeRepository.observePlaces(cardId)

    /**
     * Moves a place to [point] and pins it, see [PlaceRepository.movePlace].
     *
     * @return `false` if [point] isn't a place on the map, or the place no longer exists; nothing changed.
     */
    suspend fun move(placeId: Long, point: GeoPoint): Boolean {
        val valid = GeoMath.normalised(point) ?: return false
        val moved = placeRepository.movePlace(placeId, valid)
        findNearbyCards.invalidate()
        return moved
    }

    /**
     * Adds a pinned place for [cardId] at [point].
     *
     * @return the new place's id, or `null` if [point] isn't a place on the map; nothing changed.
     */
    suspend fun add(cardId: CardId, point: GeoPoint): Long? {
        val valid = GeoMath.normalised(point) ?: return null
        val id = placeRepository.addPlace(cardId, valid)
        findNearbyCards.invalidate()
        return id
    }

    /** The Wi‑Fi access points of a place, removed ones too, the most seen first. */
    fun observeNetworks(placeId: Long): Flow<List<StoredAccessPoint>> = placeRepository.observeAccessPoints(placeId)

    /**
     * Removes networks that were learned at a place but don't belong to it, e.g. a neighbour's, so they no
     * longer suggest the card; or restores them when [removed] is `false`.
     */
    suspend fun setNetworksRemoved(placeId: Long, bssids: Collection<Bssid>, removed: Boolean) {
        placeRepository.setAccessPointsBlocked(placeId, bssids, removed)
        findNearbyCards.invalidate()
    }

    /** Forgets one place, e.g. one the user doesn't want suggested. */
    suspend fun delete(placeId: Long) {
        placeRepository.forget(placeId)
        findNearbyCards.invalidate()
    }
}
