package io.github.danyk20.cardholder.feature.barcodefullscreen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.danyk20.cardholder.core.domain.model.SecureResult
import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.domain.security.SessionLockEvents
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardDetails
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.feature.barcodefullscreen.navigation.BarcodeDestination
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@HiltViewModel
class BarcodeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cardRepository: CardRepository,
    sessionLockEvents: SessionLockEvents,
) : ViewModel() {
    val cardId = CardId(savedStateHandle.toRoute<BarcodeDestination>().cardId)

    private val _uiState = MutableStateFlow<BarcodeUiState>(BarcodeUiState.Loading)
    val uiState: StateFlow<BarcodeUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    /** Set once a code was shown, so a card that disappears afterwards closes the screen. */
    private var wasShown = false

    init {
        observeCard()
        viewModelScope.launch {
            sessionLockEvents.events.collect {
                // A locked card's code must not stay visible after the app was in the background.
                if ((_uiState.value as? BarcodeUiState.Ready)?.isLocked == true) {
                    _uiState.value = BarcodeUiState.AuthenticationRequired(fromLock = true)
                }
            }
        }
    }

    fun onAuthenticationResult(succeeded: Boolean) {
        if (succeeded) {
            observeCard()
        } else {
            _uiState.value = BarcodeUiState.AuthenticationRequired(fromLock = false)
        }
    }

    /**
     * Follows the card while the screen is open: the details screen can be opened from here, and
     * editing or deleting the card there must not leave a stale code on screen for the scanner.
     */
    private fun observeCard() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            cardRepository.observeCard(cardId).distinctUntilChanged().collectLatest(::show)
        }
    }

    private suspend fun show(card: Card?) {
        val info = card?.info as? CardInfo.Loyalty
        if (card == null || info == null) {
            _uiState.value = if (wasShown) BarcodeUiState.Removed else BarcodeUiState.NotFound
            return
        }
        _uiState.value = when (val details = cardRepository.readDetails(cardId)) {
            is SecureResult.Success -> {
                wasShown = true
                BarcodeUiState.Ready(
                    title = card.title,
                    shopName = info.shop.name,
                    code = (details.value as CardDetails.Loyalty).code,
                    format = info.format,
                    isLocked = card.isLocked,
                )
            }

            SecureResult.AuthenticationRequired -> BarcodeUiState.AuthenticationRequired(fromLock = true)

            SecureResult.KeyInvalidated -> BarcodeUiState.KeyInvalidated
        }
    }
}

sealed interface BarcodeUiState {
    data object Loading : BarcodeUiState

    data class Ready(
        val title: String,
        val shopName: String,
        val code: String,
        val format: BarcodeFormat,
        val isLocked: Boolean,
    ) : BarcodeUiState

    /** [fromLock] is `true` when the prompt should be shown automatically. */
    data class AuthenticationRequired(val fromLock: Boolean) : BarcodeUiState

    data object KeyInvalidated : BarcodeUiState

    data object NotFound : BarcodeUiState

    /** The card was deleted, or is no longer a loyalty card, while its code was on screen. */
    data object Removed : BarcodeUiState
}
