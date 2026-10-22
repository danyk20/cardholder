package io.github.danyk20.cardholder.core.domain.usecase

import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.model.CardSort
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.model.CountryCode
import java.text.Collator
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The cards of the given [CardType]s matching a free-text query over title, shop and network, ordered by
 * [CardSort] with favourites first (except in the custom order).
 *
 * With a [country] and no query, only the cards valid there are returned, and [CardQueryResult.hiddenElsewhere]
 * counts the others of the types. A query searches all cards, so it ignores the country.
 */
class ObserveCardsUseCase
@Inject
constructor(private val cardRepository: CardRepository) {
    operator fun invoke(
        query: String = "",
        types: Set<CardType> = CardType.entries.toSet(),
        sort: CardSort = CardSort.NAME,
        country: CountryCode? = null,
    ): Flow<CardQueryResult> = cardRepository.observeCards().map { cards ->
        val needle = query.trim()
        val ofType = cards.filter { card -> card.type in types }
        if (needle.isNotEmpty()) {
            CardQueryResult(ofType.filter { it.matches(needle) }.sortedWith(comparatorFor(sort)), hiddenElsewhere = 0)
        } else {
            val (valid, elsewhere) = ofType.partition { it.validity.isValidIn(country) }
            CardQueryResult(valid.sortedWith(comparatorFor(sort)), hiddenElsewhere = elsewhere.size)
        }
    }

    private fun comparatorFor(sort: CardSort): Comparator<Card> {
        val byTitle = compareBy<Card, String>(Collator.getInstance()) { it.title }
        val order = when (sort) {
            CardSort.NAME -> byTitle.thenBy { it.createdAt }

            CardSort.DATE_ADDED -> compareByDescending<Card> { it.createdAt }.then(byTitle)

            // Cards sharing a position (e.g. from before custom ordering existed) fall back to their age.
            CardSort.CUSTOM -> return compareBy<Card> { it.position }.thenBy { it.createdAt }

            CardSort.MOST_USED -> compareByDescending<Card> { it.useCount }
                .thenByDescending { it.lastUsedAt }
                .then(byTitle)
        }
        // Favourites come first, except in the order the user arranged by hand.
        return compareByDescending<Card> { it.isFavourite }.then(order)
    }

    private fun Card.matches(needle: String): Boolean {
        val searchable = buildList {
            add(title)
            when (val info = info) {
                is CardInfo.Bank -> {
                    add(info.network.displayName)
                    info.issuer?.let { add(it.name) }
                }

                is CardInfo.Id -> add(info.country.value)

                is CardInfo.Loyalty -> add(info.shop.name)
            }
        }
        return searchable.any { it.contains(needle, ignoreCase = true) }
    }
}

/** The cards to show, and how many cards of the chosen types a country filter hid. */
data class CardQueryResult(val cards: List<Card>, val hiddenElsewhere: Int)
