package io.github.danyk20.cardholder.feature.cardlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.danyk20.cardholder.core.domain.model.getOrNull
import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.domain.usecase.ObserveCardsUseCase
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardDetails
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.ui.CardSummary
import io.github.danyk20.cardholder.core.ui.CardSummaryFactory
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class CardListViewModel @Inject constructor(
    private val observeCards: ObserveCardsUseCase,
    private val cardRepository: CardRepository,
    private val summaryFactory: CardSummaryFactory,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val query = savedStateHandle.getStateFlow(KEY_QUERY, "")

    /** Card types currently shown; all of them by default. */
    private val visibleTypes = savedStateHandle.getStateFlow(KEY_VISIBLE_TYPES, ArrayList(CardType.entries))

    val uiState: StateFlow<CardListUiState> = combine(query, visibleTypes) { query, types -> query to types.toSet() }
        .flatMapLatest { (query, types) ->
            combine(
                observeCards(query, types).mapLatest { cards ->
                    summaryFactory.summarize(cards).map { CardListItem(it, loyaltyCode(it.card)) }
                },
                observeCards().map { it.isNotEmpty() },
            ) { cards, hasAnyCards ->
                CardListUiState.Success(cards = cards, query = query, visibleTypes = types, hasAnyCards = hasAnyCards)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CardListUiState.Loading)

    /**
     * The code of an unlocked loyalty card, shown directly on its face so it can be scanned from the
     * list. Codes of locked cards stay encrypted until the user authenticates on the barcode screen.
     */
    private suspend fun loyaltyCode(card: Card): LoyaltyCode? {
        val info = card.info as? CardInfo.Loyalty
        if (info == null || card.isLocked) return null
        val details = cardRepository.readDetails(card.id).getOrNull() as? CardDetails.Loyalty ?: return null
        return LoyaltyCode(details.code, info.format)
    }

    fun onQueryChange(query: String) {
        savedStateHandle[KEY_QUERY] = query
    }

    /** Shows or hides all cards of [type]. */
    fun onTypeToggled(type: CardType) {
        val current = visibleTypes.value
        savedStateHandle[KEY_VISIBLE_TYPES] = ArrayList(if (type in current) current - type else current + type)
    }

    private companion object {
        const val KEY_QUERY = "query"
        const val KEY_VISIBLE_TYPES = "visible_types"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** A card in the list; [code] is set for unlocked loyalty cards. */
data class CardListItem(val summary: CardSummary, val code: LoyaltyCode? = null)

data class LoyaltyCode(val value: String, val format: BarcodeFormat) {
    override fun toString(): String = "LoyaltyCode(format=$format)"
}

sealed interface CardListUiState {
    data object Loading : CardListUiState

    data class Success(
        val cards: List<CardListItem>,
        val query: String,
        val visibleTypes: Set<CardType>,
        val hasAnyCards: Boolean,
    ) : CardListUiState
}
