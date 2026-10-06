package io.github.danyk20.cardholder.core.domain.usecase

import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.model.CardType
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Observes cards filtered by [CardType] and a free-text query over title, shop and network. */
class ObserveCardsUseCase
@Inject
constructor(private val cardRepository: CardRepository) {
    operator fun invoke(query: String = "", type: CardType? = null): Flow<List<Card>> =
        cardRepository.observeCards().map { cards ->
            val needle = query.trim()
            cards.filter { card ->
                (type == null || card.type == type) && (needle.isEmpty() || card.matches(needle))
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
