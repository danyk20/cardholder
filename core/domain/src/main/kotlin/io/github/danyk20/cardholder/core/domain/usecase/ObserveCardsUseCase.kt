package io.github.danyk20.cardholder.core.domain.usecase

import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.model.CardSort
import io.github.danyk20.cardholder.core.model.CardType
import java.text.Collator
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Observes cards of the given [CardType]s matching a free-text query over title, shop and network,
 * ordered by [CardSort].
 */
class ObserveCardsUseCase
@Inject
constructor(private val cardRepository: CardRepository) {
    operator fun invoke(
        query: String = "",
        types: Set<CardType> = CardType.entries.toSet(),
        sort: CardSort = CardSort.NAME,
    ): Flow<List<Card>> = cardRepository.observeCards().map { cards ->
        val needle = query.trim()
        cards.filter { card -> card.type in types && (needle.isEmpty() || card.matches(needle)) }
            .sortedWith(comparatorFor(sort))
    }

    private fun comparatorFor(sort: CardSort): Comparator<Card> {
        val byTitle = compareBy<Card, String>(Collator.getInstance()) { it.title }
        return when (sort) {
            CardSort.NAME -> byTitle.thenBy { it.createdAt }

            CardSort.DATE_ADDED -> compareByDescending<Card> { it.createdAt }.then(byTitle)

            // Cards sharing a position (e.g. from before custom ordering existed) fall back to their age.
            CardSort.CUSTOM -> compareBy<Card> { it.position }.thenBy { it.createdAt }
        }
    }

    private fun Card.matches(needle: String): Boolean {
        val searchable = buildList {
            add(title)
            when (val info = info) {
                is CardInfo.Bank -> add(info.network.displayName)
                is CardInfo.Id -> add(info.country.value)
                is CardInfo.Loyalty -> add(info.shop.name)
            }
        }
        return searchable.any { it.contains(needle, ignoreCase = true) }
    }
}
