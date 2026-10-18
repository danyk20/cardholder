package io.github.danyk20.cardholder.feature.cardlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.danyk20.cardholder.core.domain.location.NearbyCard
import io.github.danyk20.cardholder.core.domain.location.NearbyReason
import io.github.danyk20.cardholder.core.domain.model.BackupResult
import io.github.danyk20.cardholder.core.domain.model.getOrNull
import io.github.danyk20.cardholder.core.domain.repository.BackupRepository
import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.domain.repository.ShareFileStore
import io.github.danyk20.cardholder.core.domain.repository.UserPreferencesRepository
import io.github.danyk20.cardholder.core.domain.usecase.FindNearbyCardsUseCase
import io.github.danyk20.cardholder.core.domain.usecase.ObserveCardsUseCase
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardDetails
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.model.CardSort
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.ui.CardSummary
import io.github.danyk20.cardholder.core.ui.CardSummaryFactory
import io.github.danyk20.cardholder.core.ui.launchSafely
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@HiltViewModel
class CardListViewModel @Inject constructor(
    private val observeCards: ObserveCardsUseCase,
    private val cardRepository: CardRepository,
    private val summaryFactory: CardSummaryFactory,
    private val preferencesRepository: UserPreferencesRepository,
    private val backupRepository: BackupRepository,
    private val shareFileStore: ShareFileStore,
    private val savedStateHandle: SavedStateHandle,
    private val findNearbyCards: FindNearbyCardsUseCase,
) : ViewModel() {
    private val query = savedStateHandle.getStateFlow(KEY_QUERY, "")

    /** Card types currently shown; all of them by default. */
    private val visibleTypes = savedStateHandle.getStateFlow(KEY_VISIBLE_TYPES, ArrayList(CardType.entries))

    private val sort = preferencesRepository.preferences.map { it.cardSort }.distinctUntilChanged()

    /** Ids of the cards selected for sharing; empty when not in selection mode. */
    private val selection = savedStateHandle.getStateFlow(KEY_SELECTION, ArrayList<String>())

    private val share = MutableStateFlow<ShareState>(ShareState.Idle)

    /** The working order while the user rearranges cards; `null` when not reordering. */
    private val reorderDraft = MutableStateFlow<List<CardSummary>?>(null)

    /** Cards used where the user seems to be; looked up while the list is on screen. */
    private val nearby = MutableStateFlow<List<NearbyCard>>(emptyList())
    private var nearbyJob: Job? = null
    private var isVisible = false

    private val cards = combine(query, visibleTypes, sort) { query, types, sort -> Triple(query, types.toSet(), sort) }
        .flatMapLatest { (query, types, sort) ->
            combine(
                observeCards(query, types, sort).mapLatest { cards ->
                    summaryFactory.summarize(cards).map { CardListItem(it, loyaltyCode(it.card)) }
                },
                observeCards().map { it.isNotEmpty() },
            ) { cards, hasAnyCards ->
                CardListUiState.Success(
                    cards = cards,
                    query = query,
                    visibleTypes = types,
                    hasAnyCards = hasAnyCards,
                    sort = sort,
                )
            }
        }

    val uiState: StateFlow<CardListUiState> = combine(cards, reorderDraft, selection, share, nearby) {
            state,
            draft,
            ids,
            share,
            nearby,
        ->
        // Only cards that are currently shown can be shared, so hidden ones never leave by surprise.
        val selected = state.cards.map { it.summary.card }.filter { it.id.value in ids }
        state.withNearby(if (state.query.isBlank() && ids.isEmpty()) nearby else emptyList())
            .copy(reordering = draft, selected = selected, share = share) as CardListUiState
    }
        // E.g. the database can't be read: show an error instead of crashing.
        .catch { emit(CardListUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CardListUiState.Loading)

    /** The list came on screen: suggests cards used nearby, if the user switched that on. */
    fun onVisible() {
        isVisible = true
        refreshNearby()
    }

    fun onHidden() {
        isVisible = false
    }

    private fun refreshNearby() {
        nearbyJob?.cancel()
        nearbyJob = launchSafely(onError = { nearby.value = emptyList() }) {
            nearby.value = findNearbyCards(visibleTypes.value.toSet())
        }
    }

    fun onSortChange(sort: CardSort) {
        launchSafely(onError = { reorderDraft.value = null }) { preferencesRepository.setCardSort(sort) }
    }

    /** Switches to the custom order and lets the user rearrange all cards (filters and search don't apply). */
    fun onStartReorder() {
        launchSafely(onError = { reorderDraft.value = null }) {
            preferencesRepository.setCardSort(CardSort.CUSTOM)
            reorderDraft.value = summaryFactory.summarize(observeCards(sort = CardSort.CUSTOM).first())
        }
    }

    fun onMove(from: Int, to: Int) = reorderDraft.update { draft ->
        if (draft == null || from !in draft.indices || to !in draft.indices) {
            draft
        } else {
            draft.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    /** Accessible alternative to dragging: moves one card up ([offset] -1) or down (+1). */
    fun onMoveBy(id: CardId, offset: Int) {
        val index = reorderDraft.value?.indexOfFirst { it.card.id == id } ?: return
        onMove(index, index + offset)
    }

    fun onReorderDone() {
        val draft = reorderDraft.value ?: return
        launchSafely(onError = { reorderDraft.value = null }) {
            cardRepository.reorder(draft.map { it.card.id })
            reorderDraft.value = null
        }
    }

    fun onReorderCancel() {
        reorderDraft.value = null
    }

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

    /** Long-press: starts selection mode, or toggles the card when already selecting. */
    fun onToggleSelection(id: CardId) {
        val current = selection.value
        savedStateHandle[KEY_SELECTION] = ArrayList(if (id.value in current) current - id.value else current + id.value)
    }

    fun onClearSelection() {
        savedStateHandle[KEY_SELECTION] = ArrayList<String>()
    }

    /**
     * Writes the selected cards to a file for sharing; [password] (wiped afterwards) encrypts it. The
     * caller authenticated first if any card is locked or has a CVV.
     */
    fun onShare(password: CharArray?) {
        val ids = (uiState.value as? CardListUiState.Success)?.selected?.map { it.id }
        if (ids.isNullOrEmpty() || share.value == ShareState.InProgress) {
            password?.fill(' ')
            return
        }
        share.value = ShareState.InProgress
        launchSafely(onError = { share.value = ShareState.Failed }) {
            val uri: String
            val result = try {
                uri = shareFileStore.newShareFile()
                backupRepository.exportCards(ids, uri, password)
            } finally {
                password?.fill(' ')
            }
            share.value = when (result) {
                is BackupResult.Exported -> ShareState.Ready(uri, result.cardCount)
                BackupResult.AuthenticationRequired, BackupResult.KeyInvalidated -> ShareState.AuthenticationFailed
                else -> ShareState.Failed
            }
        }
    }

    /** The share sheet was opened (or the error shown): leave selection mode. */
    fun onShareHandled() {
        if (share.value is ShareState.Ready) onClearSelection()
        share.value = ShareState.Idle
    }

    fun onQueryChange(query: String) {
        savedStateHandle[KEY_QUERY] = query
    }

    /** Shows or hides all cards of [type]. */
    fun onTypeToggled(type: CardType) {
        val current = visibleTypes.value
        savedStateHandle[KEY_VISIBLE_TYPES] = ArrayList(if (type in current) current - type else current + type)
        if (isVisible) refreshNearby()
    }

    private companion object {
        const val KEY_QUERY = "query"
        const val KEY_VISIBLE_TYPES = "visible_types"
        const val KEY_SELECTION = "selection"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** A card in the list; [code] is set for unlocked loyalty cards. */
data class CardListItem(val summary: CardSummary, val code: LoyaltyCode? = null)

/** A card suggested at the top of the list, and why. */
data class NearbyItem(val item: CardListItem, val reason: NearbyReason)

/** Moves the [nearby] cards that are shown out of the list into the suggestions, keeping their order. */
private fun CardListUiState.Success.withNearby(nearby: List<NearbyCard>): CardListUiState.Success {
    val shown = cards.associateBy { it.summary.card.id }
    val suggested = nearby.mapNotNull { suggestion ->
        shown[suggestion.cardId]?.let { NearbyItem(it, suggestion.reason) }
    }
    if (suggested.isEmpty()) return copy(nearby = emptyList())
    val ids = suggested.map { it.item.summary.card.id }.toSet()
    return copy(nearby = suggested, cards = cards.filterNot { it.summary.card.id in ids })
}

data class LoyaltyCode(val value: String, val format: BarcodeFormat) {
    override fun toString(): String = "LoyaltyCode(format=$format)"
}

sealed interface CardListUiState {
    data object Loading : CardListUiState

    /** The cards couldn't be loaded. */
    data object Error : CardListUiState

    data class Success(
        val cards: List<CardListItem>,
        val query: String,
        val visibleTypes: Set<CardType>,
        val hasAnyCards: Boolean,
        val sort: CardSort = CardSort.NAME,
        /** Cards in their working order while the user rearranges them; `null` otherwise. */
        val reordering: List<CardSummary>? = null,
        /** Cards selected for sharing; selection mode is on while not empty. */
        val selected: List<Card> = emptyList(),
        val share: ShareState = ShareState.Idle,
        /** Cards suggested because they were used nearby; not repeated in [cards]. */
        val nearby: List<NearbyItem> = emptyList(),
    ) : CardListUiState
}

sealed interface ShareState {
    data object Idle : ShareState

    data object InProgress : ShareState

    /** The file at [uri] is ready to be handed to the share sheet. */
    data class Ready(val uri: String, val cardCount: Int) : ShareState

    data object AuthenticationFailed : ShareState

    data object Failed : ShareState
}
