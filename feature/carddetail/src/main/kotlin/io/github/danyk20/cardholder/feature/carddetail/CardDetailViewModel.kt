package io.github.danyk20.cardholder.feature.carddetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
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
import io.github.danyk20.cardholder.core.ui.launchSafely
import io.github.danyk20.cardholder.feature.carddetail.navigation.CardDetailDestination
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update

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

    /**
     * Whether the user authenticated on this screen since it opened (or since the app was last in the
     * background). Protected data is only decrypted after that: the Keystore key alone would also open
     * it for a while after the phone was unlocked, which must not count as consent.
     */
    private var authenticatedHere = false

    /** Each visit counts once towards the "most used" order. */
    private var useRecorded = false

    init {
        launchSafely(onError = ::onUnexpectedError) {
            cardRepository.observeCard(cardId).distinctUntilChanged().collect { card ->
                if (card == null) {
                    _uiState.update { it.copy(notFound = true) }
                } else {
                    _uiState.update { it.copy(summary = summaryFactory.summarize(card)) }
                    loadDetails(card)
                }
            }
        }
        launchSafely(onError = ::onUnexpectedError) {
            sessionLockEvents.events.collect { onSessionLocked() }
        }
    }

    fun onResume() = _uiState.update { it.copy(canProtect = deviceSecurity.isDeviceSecure()) }

    fun onUnlockDetails() = _uiState.update { it.copy(pendingAuthentication = AuthAction.UNLOCK_DETAILS) }

    fun onRevealCvv() {
        if (!authenticatedHere) {
            _uiState.update { it.copy(pendingAuthentication = AuthAction.REVEAL_CVV) }
            return
        }
        launchSafely(onError = ::onUnexpectedError) { revealCvv(requestAuthentication = true) }
    }

    fun onHideCvv() = _uiState.update { it.copy(cvv = null) }

    fun onFavouriteChange(favourite: Boolean) {
        launchSafely(onError = ::onUnexpectedError) { cardRepository.setFavourite(cardId, favourite) }
    }

    fun onLockedChange(locked: Boolean) {
        // Removing a lock decrypts the card, so it needs the user's confirmation like viewing it.
        if (!locked && !authenticatedHere) {
            _uiState.update { it.copy(pendingAuthentication = AuthAction.REMOVE_LOCK) }
            return
        }
        launchSafely(onError = ::onUnexpectedError) {
            when (cardRepository.setLocked(cardId, locked)) {
                is SecureResult.Success -> Unit

                SecureResult.AuthenticationRequired ->
                    _uiState.update { it.copy(pendingAuthentication = AuthAction.REMOVE_LOCK) }

                SecureResult.KeyInvalidated -> _uiState.update { it.copy(error = DetailError.KEY_INVALIDATED) }

                is SecureResult.Failed -> _uiState.update { it.copy(error = DetailError.UNEXPECTED) }
            }
        }
    }

    fun onAuthenticationResult(action: AuthAction, succeeded: Boolean) {
        _uiState.update { it.copy(pendingAuthentication = null) }
        if (!succeeded) return
        authenticatedHere = true
        launchSafely(onError = ::onUnexpectedError) {
            when (action) {
                AuthAction.UNLOCK_DETAILS -> {
                    val card = _uiState.value.summary?.card ?: return@launchSafely
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

    fun onErrorShown() = _uiState.update { it.copy(error = null) }

    private fun onUnexpectedError(@Suppress("UNUSED_PARAMETER") error: Throwable) =
        _uiState.update { it.copy(error = DetailError.UNEXPECTED) }

    fun onDelete() {
        launchSafely(onError = ::onUnexpectedError) {
            cardRepository.delete(cardId)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }

    private suspend fun loadDetails(card: Card) {
        val details = if (card.isLocked && !authenticatedHere) {
            DetailsState.Locked
        } else {
            when (val result = cardRepository.readDetails(card.id)) {
                is SecureResult.Success -> DetailsState.Loaded(result.value)
                SecureResult.AuthenticationRequired -> DetailsState.Locked
                SecureResult.KeyInvalidated -> DetailsState.KeyInvalidated
                is SecureResult.Failed -> DetailsState.Failed
            }
        }
        if (details is DetailsState.Loaded && !useRecorded) {
            useRecorded = true
            cardRepository.recordUse(card.id)
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

            SecureResult.KeyInvalidated -> _uiState.update { it.copy(error = DetailError.KEY_INVALIDATED) }

            is SecureResult.Failed -> _uiState.update { it.copy(error = DetailError.UNEXPECTED) }
        }
    }

    /** Drops decrypted data when the app goes to the background; coming back needs a new confirmation. */
    private fun onSessionLocked() = _uiState.update { state ->
        authenticatedHere = false
        val locked = state.summary?.card?.isLocked == true
        state.copy(cvv = null, details = if (locked) DetailsState.Locked else state.details)
    }
}
