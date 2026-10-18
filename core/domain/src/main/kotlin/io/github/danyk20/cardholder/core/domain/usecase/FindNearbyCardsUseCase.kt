package io.github.danyk20.cardholder.core.domain.usecase

import io.github.danyk20.cardholder.core.domain.location.FixRequest
import io.github.danyk20.cardholder.core.domain.location.LocationAccess
import io.github.danyk20.cardholder.core.domain.location.NearbyCard
import io.github.danyk20.cardholder.core.domain.location.NearbyMatcher
import io.github.danyk20.cardholder.core.domain.location.ShopKeywords
import io.github.danyk20.cardholder.core.domain.location.SuggestionCandidate
import io.github.danyk20.cardholder.core.domain.location.SurroundingsProvider
import io.github.danyk20.cardholder.core.domain.location.isUsable
import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.domain.repository.PlaceRepository
import io.github.danyk20.cardholder.core.domain.repository.ShopRepository
import io.github.danyk20.cardholder.core.domain.repository.UserPreferencesRepository
import io.github.danyk20.cardholder.core.model.BrandRef
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.model.PlaceSuggestions
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Cards to suggest because the user seems to be where they're used. Reads Wi‑Fi first and the
 * location only when Wi‑Fi alone isn't conclusive; the result is reused for a minute, so reopening
 * the list doesn't read the sensors again.
 */
@Singleton
class FindNearbyCardsUseCase @Inject constructor(
    private val cardRepository: CardRepository,
    private val placeRepository: PlaceRepository,
    private val shopRepository: ShopRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val surroundings: SurroundingsProvider,
    private val locationAccess: LocationAccess,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private var cached: Pair<Instant, List<NearbyCard>>? = null

    /** At most [NearbyMatcher.MAX_SUGGESTIONS] cards of the given [types]. */
    suspend operator fun invoke(types: Set<CardType>): List<NearbyCard> = mutex.withLock {
        val cards = cardRepository.observeCards().first().filter { it.suggestByLocation }
        val enabled = preferencesRepository.preferences.first().placeSuggestions != PlaceSuggestions.OFF
        if (!enabled || cards.none { it.type in types } || !locationAccess.isUsable) return emptyList()
        val nearby = cached?.takeIf { (at, _) -> Duration.between(at, clock.instant()) < REUSE_FOR }?.second
            ?: find(cards).also { cached = clock.instant() to it }
        val typeOf = cards.associate { it.id to it.type }
        nearby.filter { typeOf[it.cardId] in types }.take(NearbyMatcher.MAX_SUGGESTIONS)
    }

    /** Forgets the last result, e.g. after places were forgotten. */
    suspend fun invalidate() = mutex.withLock { cached = null }

    private suspend fun find(cards: List<Card>): List<NearbyCard> {
        val places = placeRepository.suggestionPlaces().groupBy { it.cardId }
        val candidates = cards.map { SuggestionCandidate(it, places[it.id].orEmpty(), keywords(it)) }
        val scan = surroundings.accessPoints(WIFI_MAX_AGE)
        val needsLocation = !NearbyMatcher.isCertain(candidates, scan) &&
            candidates.any { candidate -> candidate.places.any { it.centre != null } }
        val fix = if (needsLocation) surroundings.locationFix(FixRequest.Nearby) else null
        return NearbyMatcher.match(candidates, scan, fix, limit = Int.MAX_VALUE)
    }

    /** Words that name the card's shop in Wi‑Fi network names; only loyalty cards belong to a shop. */
    private suspend fun keywords(card: Card): Set<String> {
        val shop = (card.info as? CardInfo.Loyalty)?.shop ?: return emptySet()
        val extra = (shop as? BrandRef.Known)?.let { shopRepository.shop(it.id) }?.wifiNames.orEmpty()
        return ShopKeywords.of(shop.name, extra)
    }

    private companion object {
        val REUSE_FOR: Duration = Duration.ofMinutes(1)
        val WIFI_MAX_AGE: Duration = Duration.ofMinutes(2)
    }
}
