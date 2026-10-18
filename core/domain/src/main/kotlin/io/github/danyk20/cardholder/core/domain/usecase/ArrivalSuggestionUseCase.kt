package io.github.danyk20.cardholder.core.domain.usecase

import io.github.danyk20.cardholder.core.domain.location.ArrivalCandidate
import io.github.danyk20.cardholder.core.domain.location.ArrivalPolicy
import io.github.danyk20.cardholder.core.domain.location.LocationAccess
import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.domain.repository.PlaceRepository
import io.github.danyk20.cardholder.core.domain.repository.UserPreferencesRepository
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.PlaceSuggestions
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** The card to notify about after arriving at one of its places. */
data class ArrivalSuggestion(val card: Card, val placeId: Long)

/** Decides what to notify when the system reports arriving at or leaving learned places. */
class ArrivalSuggestionUseCase @Inject constructor(
    private val cardRepository: CardRepository,
    private val placeRepository: PlaceRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val locationAccess: LocationAccess,
    private val syncGeofences: SyncGeofencesUseCase,
    private val clock: Clock,
) {
    /**
     * The card to notify about after arriving at [placeIds], remembered as notified; `null` if none
     * should be, e.g. right after another notification or because notifications aren't allowed.
     */
    suspend fun onArrival(placeIds: List<Long>): ArrivalSuggestion? {
        if (preferencesRepository.preferences.first().placeSuggestions != PlaceSuggestions.ARRIVAL) {
            // Left over from before arrival notifications were turned off.
            syncGeofences.clear()
            return null
        }
        val chosen = if (locationAccess.canNotify()) choose(placeIds) else null
        chosen?.let { placeRepository.markNotified(it.place.id) }
        return chosen?.let { ArrivalSuggestion(it.card, it.place.id) }
    }

    private suspend fun choose(placeIds: List<Long>): ArrivalCandidate? {
        val cards = cardRepository.observeCards().first().associateBy { it.id }
        val candidates = placeRepository.places(placeIds).mapNotNull { place ->
            cards[place.cardId]?.let { ArrivalCandidate(it, place, placeRepository.lastNotifiedAt(it.id)) }
        }
        return ArrivalPolicy.choose(candidates, placeRepository.lastNotifiedAt(), clock.instant())
    }

    /** The cards whose notification can go after leaving [placeIds]. */
    suspend fun onDeparture(placeIds: List<Long>): Set<CardId> =
        placeRepository.places(placeIds).mapTo(HashSet()) { it.cardId }

    /** The user said the card isn't used at this place: forget it, so it's not suggested there again. */
    suspend fun notThisPlace(placeId: Long) = placeRepository.forget(placeId)
}
