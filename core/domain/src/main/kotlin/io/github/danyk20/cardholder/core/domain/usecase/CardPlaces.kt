package io.github.danyk20.cardholder.core.domain.usecase

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
}
