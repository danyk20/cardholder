package io.github.danyk20.cardholder.feature.cardlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.danyk20.cardholder.core.domain.country.CountryState
import io.github.danyk20.cardholder.core.domain.location.NearbyCard
import io.github.danyk20.cardholder.core.domain.location.NearbyReason
import io.github.danyk20.cardholder.core.domain.model.BackupResult
import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.domain.model.getOrNull
import io.github.danyk20.cardholder.core.domain.repository.BackupRepository
import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.domain.repository.CountryRepository
import io.github.danyk20.cardholder.core.domain.repository.CurrentCountryRepository
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
import io.github.danyk20.cardholder.core.model.CardValidity
import io.github.danyk20.cardholder.core.model.CountryCode
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
@Suppress("LongParameterList", "TooManyFunctions") // One collaborator per parameter; one handler per action.
class CardListViewModel @Inject constructor(
    private val observeCards: ObserveCardsUseCase,
    private val cardRepository: CardRepository,
    private val summaryFactory: CardSummaryFactory,
    private val preferencesRepository: UserPreferencesRepository,
    private val backupRepository: BackupRepository,
    private val shareFileStore: ShareFileStore,
    private val savedStateHandle: SavedStateHandle,
    private val findNearbyCards: FindNearbyCardsUseCase,
    private val currentCountry: CurrentCountryRepository,
    private val countryRepository: CountryRepository,
) : ViewModel() {
    private val query = savedStateHandle.getStateFlow(KEY_QUERY, "")

    /**
     * The search field's text, as Compose state so the field sees every keystroke at once. The list's
     * [CardListUiState.Success.query] arrives later, after the cards were filtered and decrypted; showing
     * that in the field reordered fast typing ("credit" became "reditc").
     */
    var searchText by mutableStateOf(query.value)
        private set

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

    /** The countries the picker offers. */
    private val catalogue: List<Country> = countryRepository.countries()

    private val countryFilter = combine(
        preferencesRepository.preferences.map { it.countryFilterEnabled }.distinctUntilChanged(),
        currentCountry.state.distinctUntilChanged(),
    ) { enabled, state -> CountryFilter(enabled, state) }

    /** Everything that decides which cards are listed. */
    private val listing = combine(query, visibleTypes, sort, countryFilter) { query, types, sort, filter ->
        Listing(query, types.toSet(), sort, filter)
    }

    /** All cards whatever the filters, for the country row and the empty state. */
    private val allCards = observeCards().map { it.cards }

    private val cards = listing.flatMapLatest { listing ->
        val country = listing.filter.state.effective?.code?.takeIf { listing.filter.enabled }
        combine(
            observeCards(listing.query, listing.types, listing.sort, country).mapLatest { result ->
                Shown(
                    items = summaryFactory.summarize(result.cards).map { CardListItem(it, loyaltyCode(it.card)) },
                    hiddenElsewhere = result.hiddenElsewhere,
                )
            },
            allCards,
        ) { shown, all ->
            CardListUiState.Success(
                cards = shown.items,
                query = listing.query,
                visibleTypes = listing.types,
                hasAnyCards = all.isNotEmpty(),
                sort = listing.sort,
                // The country row only appears once a card is restricted to some countries.
                country = listing.filter.takeIf { all.any { it.validity is CardValidity.Countries } }
                    ?.let { CountryChip(it.state, it.enabled) },
                countries = catalogue,
                hiddenElsewhere = shown.hiddenElsewhere,
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
        launchSafely { currentCountry.refresh() }
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
            reorderDraft.value = summaryFactory.summarize(observeCards(sort = CardSort.CUSTOM).first().cards)
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

    /** Turns the country filter on or off. The choice is remembered, and "show all" turns it off too. */
    fun onCountryFilterChange(enabled: Boolean) {
        launchSafely { preferencesRepository.setCountryFilterEnabled(enabled) }
    }

    /** Uses [code] as the current country, instead of the detected one, until detection changes. */
    fun onCountryPicked(code: CountryCode) {
        launchSafely { currentCountry.setOverride(code) }
    }

    /** Goes back to the detected country. */
    fun onAutomaticCountry() {
        launchSafely { currentCountry.clearOverride() }
    }

    fun onQueryChange(query: String) {
        searchText = query
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

private data class CountryFilter(val enabled: Boolean, val state: CountryState)

private data class Listing(val query: String, val types: Set<CardType>, val sort: CardSort, val filter: CountryFilter)

private data class Shown(val items: List<CardListItem>, val hiddenElsewhere: Int)

/** The country row above the list: what is in use, and whether the cards are filtered by it. */
data class CountryChip(val state: CountryState, val filterEnabled: Boolean)

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
        /** The country row, shown once a card is valid in some countries only; `null` hides it. */
        val country: CountryChip? = null,
        /** The countries the country picker offers. */
        val countries: List<Country> = emptyList(),
        /** Cards of the chosen types hidden because they are not valid in the current country. */
        val hiddenElsewhere: Int = 0,
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
