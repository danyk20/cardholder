package io.github.danyk20.cardholder.feature.carddetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.danyk20.cardholder.core.domain.model.SecureResult
import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.domain.security.DeviceSecurity
import io.github.danyk20.cardholder.core.domain.security.SecureClipboard
import io.github.danyk20.cardholder.core.domain.security.SessionLockEvents
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.ui.CardSummaryFactory
import io.github.danyk20.cardholder.feature.carddetail.navigation.CardDetailDestination
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
@Suppress("TooManyFunctions")
class CardDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cardRepository: CardRepository,
    private val summaryFactory: CardSummaryFactory,
    private val deviceSecurity: DeviceSecurity,
    private val clipboard: SecureClipboard,
    sessionLockEvents: SessionLockEvents,
) : ViewModel() {
    val cardId = CardId(savedStateHandle.toRoute<CardDetailDestination>().cardId)

    private val _uiState = MutableStateFlow(CardDetailUiState(canProtect = deviceSecurity.isDeviceSecure()))
    val uiState: StateFlow<CardDetailUiState> = _uiState.asStateFlow()

    private var promptedOnOpen = false

    init {
        viewModelScope.launch {
            cardRepository.observeCard(cardId).distinctUntilChanged().collect { card ->
                if (card == null) {
                    _uiState.update { it.copy(notFound = true) }
                } else {
                    _uiState.update { it.copy(summary = summaryFactory.summarize(card)) }
                    loadDetails(card)
                }
            }
        }
        viewModelScope.launch {
            sessionLockEvents.events.collect { onSessionLocked() }
        }
    }

    fun onResume() = _uiState.update { it.copy(canProtect = deviceSecurity.isDeviceSecure()) }

    fun onUnlockDetails() = _uiState.update { it.copy(pendingAuthentication = AuthAction.UNLOCK_DETAILS) }

    fun onRevealCvv() {
        viewModelScope.launch { revealCvv(requestAuthentication = true) }
    }

    fun onHideCvv() = _uiState.update { it.copy(cvv = null) }

    fun onLockedChange(locked: Boolean) {
        viewModelScope.launch {
            when (cardRepository.setLocked(cardId, locked)) {
                is SecureResult.Success -> Unit

                SecureResult.AuthenticationRequired ->
                    _uiState.update { it.copy(pendingAuthentication = AuthAction.REMOVE_LOCK) }

                SecureResult.KeyInvalidated -> _uiState.update { it.copy(showKeyInvalidatedError = true) }
            }
        }
    }

    fun onAuthenticationResult(action: AuthAction, succeeded: Boolean) {
        _uiState.update { it.copy(pendingAuthentication = null) }
        if (!succeeded) return
        viewModelScope.launch {
            when (action) {
                AuthAction.UNLOCK_DETAILS -> {
                    val card = _uiState.value.summary?.card ?: return@launch
                    loadDetails(card)
                    // Unlocking a locked card reveals everything, including the CVV.
                    if (card.hasCvv) revealCvv(requestAuthentication = false)
                }

                AuthAction.REVEAL_CVV -> revealCvv(requestAuthentication = false)

                AuthAction.REMOVE_LOCK -> onLockedChange(false)
            }
        }
    }

    fun onCopy(label: String, value: String) {
        clipboard.copy(label, value)
        _uiState.update { it.copy(copiedLabel = label) }
    }

    fun onCopiedMessageShown() = _uiState.update { it.copy(copiedLabel = null) }

    fun onKeyInvalidatedErrorShown() = _uiState.update { it.copy(showKeyInvalidatedError = false) }

    fun onDelete() {
        viewModelScope.launch {
            cardRepository.delete(cardId)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }

    private suspend fun loadDetails(card: Card) {
        val details = when (val result = cardRepository.readDetails(card.id)) {
            is SecureResult.Success -> DetailsState.Loaded(result.value)
            SecureResult.AuthenticationRequired -> DetailsState.Locked
            SecureResult.KeyInvalidated -> DetailsState.KeyInvalidated
        }
        val promptNow = details == DetailsState.Locked && !promptedOnOpen
        if (details == DetailsState.Locked) promptedOnOpen = true
        _uiState.update {
            it.copy(
                details = details,
                pendingAuthentication = if (promptNow) AuthAction.UNLOCK_DETAILS else it.pendingAuthentication,
            )
        }
    }

    private suspend fun revealCvv(requestAuthentication: Boolean) {
        when (val result = cardRepository.readCvv(cardId)) {
            is SecureResult.Success -> _uiState.update { it.copy(cvv = result.value) }

            SecureResult.AuthenticationRequired -> if (requestAuthentication) {
                _uiState.update { it.copy(pendingAuthentication = AuthAction.REVEAL_CVV) }
            }

            SecureResult.KeyInvalidated -> _uiState.update { it.copy(showKeyInvalidatedError = true) }
        }
    }

    /** Drops decrypted data when the app goes to the background. */
    private fun onSessionLocked() = _uiState.update { state ->
        val locked = state.summary?.card?.isLocked == true
        state.copy(cvv = null, details = if (locked) DetailsState.Locked else state.details)
    }
}
