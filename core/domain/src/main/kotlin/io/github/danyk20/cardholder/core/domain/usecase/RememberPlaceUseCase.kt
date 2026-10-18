package io.github.danyk20.cardholder.core.domain.usecase

import io.github.danyk20.cardholder.core.domain.di.ApplicationScope
import io.github.danyk20.cardholder.core.domain.location.FixRequest
import io.github.danyk20.cardholder.core.domain.location.LocationAccess
import io.github.danyk20.cardholder.core.domain.location.Surroundings
import io.github.danyk20.cardholder.core.domain.location.SurroundingsProvider
import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.domain.repository.PlaceRepository
import io.github.danyk20.cardholder.core.domain.repository.UserPreferencesRepository
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.PlaceSuggestions
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What became of remembering where a card is used. */
enum class RememberResult {
    REMEMBERED,

    /** Neither an accurate enough location nor Wi‑Fi was found. */
    NOTHING_FOUND,

    /** Suggestions are off in the settings or for this card. */
    SWITCHED_OFF,

    /** Precise location isn't allowed. */
    NO_PERMISSION,

    /** Location is switched off in the system settings. */
    LOCATION_OFF,
}

/** Learns where a card is being used, reading Wi‑Fi and location once. */
class RememberPlaceUseCase @Inject constructor(
    private val cardRepository: CardRepository,
    private val placeRepository: PlaceRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val surroundings: SurroundingsProvider,
    private val locationAccess: LocationAccess,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    /** Learns in the background, e.g. while a code is shown at the till; leaving the screen doesn't stop it. */
    fun rememberInBackground(cardId: CardId) {
        applicationScope.launch {
            // A failure (e.g. the card was just deleted) only means this use isn't learned.
            runCatching { invoke(cardId) }
        }
    }

    suspend operator fun invoke(cardId: CardId): RememberResult {
        val mode = preferencesRepository.preferences.first().placeSuggestions
        val card = cardRepository.observeCard(cardId).first()
        val blocker = when {
            mode == PlaceSuggestions.OFF || card == null || !card.suggestByLocation -> RememberResult.SWITCHED_OFF
            !locationAccess.permission().isPrecise -> RememberResult.NO_PERMISSION
            !locationAccess.isLocationEnabled() -> RememberResult.LOCATION_OFF
            else -> null
        }
        return blocker ?: remember(cardId)
    }

    private suspend fun remember(cardId: CardId): RememberResult {
        val sensed = coroutineScope {
            val accessPoints = async { surroundings.accessPoints(WIFI_MAX_AGE) }
            val fix = async { surroundings.locationFix(FixRequest.Learn) }
            Surroundings(accessPoints.await(), fix.await())
        }
        val remembered = placeRepository.recordVisit(cardId, sensed)
        return if (remembered) RememberResult.REMEMBERED else RememberResult.NOTHING_FOUND
    }

    private companion object {
        /** The networks around right now; older scans may be from before entering the shop. */
        val WIFI_MAX_AGE: Duration = Duration.ofMinutes(1)
    }
}
