package io.github.danyk20.cardholder.feature.cardlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.danyk20.cardholder.core.domain.usecase.ObserveCardsUseCase
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.ui.CardSummary
import io.github.danyk20.cardholder.core.ui.CardSummaryFactory
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class CardListViewModel @Inject constructor(
    private val observeCards: ObserveCardsUseCase,
    private val summaryFactory: CardSummaryFactory,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val query = savedStateHandle.getStateFlow(KEY_QUERY, "")
    private val filter = savedStateHandle.getStateFlow<CardType?>(KEY_FILTER, null)

    val uiState: StateFlow<CardListUiState> = combine(query, filter, ::Pair)
        .flatMapLatest { (query, filter) ->
            combine(
                observeCards(query, filter).map { summaryFactory.summarize(it) },
                observeCards().map { it.isNotEmpty() },
            ) { cards, hasAnyCards ->
                CardListUiState.Success(cards = cards, query = query, filter = filter, hasAnyCards = hasAnyCards)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CardListUiState.Loading)

    fun onQueryChange(query: String) {
        savedStateHandle[KEY_QUERY] = query
    }

    fun onFilterChange(filter: CardType?) {
        savedStateHandle[KEY_FILTER] = filter
    }

    private companion object {
        const val KEY_QUERY = "query"
        const val KEY_FILTER = "filter"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

sealed interface CardListUiState {
    data object Loading : CardListUiState

    data class Success(
        val cards: List<CardSummary>,
        val query: String,
        val filter: CardType?,
        val hasAnyCards: Boolean,
    ) : CardListUiState
}
