package io.github.danyk20.cardholder.feature.cardeditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.danyk20.cardholder.core.domain.model.CardDraft
import io.github.danyk20.cardholder.core.domain.model.SecureResult
import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.domain.repository.LogoDownloader
import io.github.danyk20.cardholder.core.domain.repository.UserPreferencesRepository
import io.github.danyk20.cardholder.core.domain.security.DeviceSecurity
import io.github.danyk20.cardholder.core.domain.usecase.SaveCardResult
import io.github.danyk20.cardholder.core.domain.usecase.SaveCardUseCase
import io.github.danyk20.cardholder.core.domain.validation.CardDraftValidator
import io.github.danyk20.cardholder.core.domain.validation.CardField
import io.github.danyk20.cardholder.core.domain.validation.ValidationError
import io.github.danyk20.cardholder.core.model.Bank
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.BrandRef
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardColor
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CardSide
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.model.Shop
import io.github.danyk20.cardholder.core.ui.launchSafely
import io.github.danyk20.cardholder.feature.cardeditor.navigation.CardEditorDestination
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

@HiltViewModel
@Suppress("TooManyFunctions") // One handler per user action on the screen.
class CardEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cardRepository: CardRepository,
    private val saveCard: SaveCardUseCase,
    private val catalogues: EditorCatalogues,
    private val deviceSecurity: DeviceSecurity,
    private val validator: CardDraftValidator,
    private val photoReader: CardPhotoReader,
    private val preferencesRepository: UserPreferencesRepository,
    private val logoDownloader: LogoDownloader,
) : ViewModel() {
    private val editingId: CardId? = savedStateHandle.toRoute<CardEditorDestination>().cardId?.let(::CardId)

    private val _uiState = MutableStateFlow(
        CardEditorUiState(
            editingId = editingId,
            step = if (editingId != null) EditorStep.DETAILS else EditorStep.TYPE,
            loadState = if (editingId != null) LoadState.LOADING else LoadState.READY,
            canProtect = deviceSecurity.isDeviceSecure(),
        ),
    )
    val uiState: StateFlow<CardEditorUiState> = _uiState.asStateFlow()

    private var original: Card? = null

    /** The content as loaded for editing; `null` for a new card. */
    private var savedContent: EditorContent? = null

    /**
     * Whether the user authenticated in the editor. A locked card is only decrypted for editing after
     * that, not merely because the Keystore key is still usable after the phone was unlocked.
     */
    private var authenticatedHere = false

    init {
        launchSafely(onError = { onUnexpectedError() }) {
            val shops = catalogues.shops.shops()
            val banks = catalogues.banks.banks()
            _uiState.update { it.copy(shops = shops, banks = banks, countries = catalogues.countries.countries()) }
            if (editingId != null) load(editingId)
        }
        launchSafely {
            preferencesRepository.preferences.collect { prefs ->
                _uiState.update { it.copy(expiryRemindersEnabled = prefs.expiryReminders) }
            }
        }
    }

    /** Re-checks the screen lock, e.g. after the user returns from the system settings. */
    fun onResume() = _uiState.update { it.copy(canProtect = deviceSecurity.isDeviceSecure()) }

    fun onTypeSelected(type: CardType) = _uiState.update { it.copy(type = type, step = EditorStep.SIDES) }

    fun onSidesDone() = _uiState.update { it.copy(step = EditorStep.DETAILS) }

    /**
     * Returns `false` when the screen should close: there is no previous step and nothing would be
     * lost. With unsaved changes the user is asked first ([CardEditorUiState.confirmDiscard]).
     */
    fun onBack(): Boolean {
        val state = _uiState.value
        if (!state.isEditing && state.step != EditorStep.TYPE) {
            _uiState.update { it.copy(step = EditorStep.entries[state.step.ordinal - 1]) }
            return true
        }
        if (!hasUnsavedChanges(state)) return false
        _uiState.update { it.copy(confirmDiscard = true) }
        return true
    }

    fun onKeepEditing() = _uiState.update { it.copy(confirmDiscard = false) }

    private fun hasUnsavedChanges(state: CardEditorUiState): Boolean {
        if (state.loadState != LoadState.READY) return false
        // Picking only a type for a new card doesn't count as an edit.
        val baseline = savedContent ?: CardEditorUiState().content.copy(type = state.type)
        return state.content != baseline
    }

    fun onSideImagePicked(side: CardSide, uri: String) {
        updateSide(side, SideImage.New(uri))
        // Reading the photo is a convenience; if it fails the user simply types the details.
        launchSafely { prefillFromPhotos() }
    }

    fun onPrefillMessageShown() = _uiState.update { it.copy(prefilledFromPhoto = false) }

    /** Fills empty fields with what can be read from the new photos; never overwrites the user's input. */
    private suspend fun prefillFromPhotos() {
        val state = _uiState.value
        val prefill = photoReader.read(
            type = state.type,
            front = (state.front as? SideImage.New)?.uri,
            back = (state.back as? SideImage.New)?.uri,
            shops = state.shops,
            banks = state.banks,
        )
        _uiState.update { current ->
            val filled = current.prefilledWith(prefill)
            if (filled.content == current.content) current else filled.copy(prefilledFromPhoto = true)
        }
    }

    fun onSideImageRemoved(side: CardSide) = updateSide(side, SideImage.None)

    fun onTitleChange(title: String) = update(CardField.TITLE) { copy(title = title) }

    fun onColorChange(color: CardColor) = _uiState.update { it.copy(color = color) }

    fun onNotesChange(notes: String) = _uiState.update { it.copy(notes = notes.take(MAX_NOTES_LENGTH)) }

    fun onLockedChange(locked: Boolean) = _uiState.update { it.copy(isLocked = locked && it.canProtect) }

    fun onNumberChange(number: String) =
        update(CardField.NUMBER) { copy(bank = bank.copy(number = number.filter(Char::isDigit).take(MAX_PAN))) }

    fun onExpiryChange(expiry: String) =
        update(CardField.EXPIRY) { copy(bank = bank.copy(expiry = expiry.filter(Char::isDigit).take(EXPIRY_DIGITS))) }

    fun onHolderChange(holder: String) = update(CardField.HOLDER) { copy(bank = bank.copy(holder = holder)) }

    fun onCvvChange(cvv: String) =
        update(CardField.CVV) { copy(bank = bank.copy(cvv = cvv.filter(Char::isDigit).take(MAX_CVV))) }

    /** Prefills the bank form with what an NFC read returned; the CVV is never on the chip. */
    fun onBankCardRead(number: String, expiry: YearMonth?, holder: String?) = _uiState.update { state ->
        state.copy(
            bank = state.bank.copy(
                number = number.filter(Char::isDigit).take(MAX_PAN),
                expiry = expiry?.toExpiryDigits() ?: state.bank.expiry,
                holder = holder ?: state.bank.holder,
            ),
            errors = state.errors - CardField.NUMBER - CardField.EXPIRY - CardField.HOLDER,
        )
    }

    fun onRemoveStoredCvv() = _uiState.update { it.copy(bank = it.bank.copy(cvv = "", removeStoredCvv = true)) }

    fun onCountrySelected(code: String) = update(CardField.COUNTRY) {
        copy(id = id.copy(country = countries.firstOrNull { it.code.value == code }))
    }

    fun onDocumentNumberChange(value: String) =
        update(CardField.DOCUMENT_NUMBER) { copy(id = id.copy(documentNumber = value)) }

    fun onIdExpiryChange(date: LocalDate?) = _uiState.update { it.copy(id = it.id.copy(expiry = date)) }

    fun onShopSelected(shop: Shop) = update(CardField.SHOP) {
        copy(
            loyalty = loyalty.copy(
                shop = BrandRef.Known(shop.id, shop.name),
                format = if (loyalty.code.isEmpty()) shop.defaultFormat else loyalty.format,
            ),
            logoChoiceFor = shop.logo?.let { LogoChoice(shop.name, it) },
        )
    }

    fun onBankSelected(bank: Bank) = _uiState.update {
        it.copy(
            bank = it.bank.copy(issuer = BrandRef.Known(bank.id, bank.name)),
            logoChoiceFor = bank.logo?.let { logo -> LogoChoice(bank.name, logo) },
        )
    }

    fun onCustomBank(name: String) = _uiState.update {
        it.copy(bank = it.bank.copy(issuer = BrandRef.Custom(name.trim())))
    }

    /** Downloads the selected shop's or bank's official logo; the app's only network request. */
    fun onUseOfficialLogo() {
        val logo = (_uiState.value.logoChoiceFor ?: _uiState.value.officialLogo)?.logo ?: return
        _uiState.update { it.copy(logoChoiceFor = null, isDownloadingLogo = true, logoDownloadFailed = false) }
        launchSafely(onError = { onUnexpectedError() }) {
            val bytes = logoDownloader.download(logo.url)
            _uiState.update {
                if (bytes != null) {
                    it.copy(isDownloadingLogo = false, logo = LogoImage.Downloaded(bytes))
                } else {
                    it.copy(isDownloadingLogo = false, logoDownloadFailed = true)
                }
            }
        }
    }

    fun onLogoPicked(uri: String) = _uiState.update { it.copy(logoChoiceFor = null, logo = LogoImage.Picked(uri)) }

    fun onRemoveLogo() = _uiState.update { it.copy(logoChoiceFor = null, logo = LogoImage.None) }

    fun onLogoChoiceDismissed() = _uiState.update { it.copy(logoChoiceFor = null) }

    fun onLogoDownloadErrorShown() = _uiState.update { it.copy(logoDownloadFailed = false) }

    fun onCustomShop(name: String) =
        update(CardField.SHOP) { copy(loyalty = loyalty.copy(shop = BrandRef.Custom(name.trim()))) }

    fun onCodeChange(code: String) = update(CardField.CODE) { copy(loyalty = loyalty.copy(code = code)) }

    fun onFormatChange(format: BarcodeFormat) = update(CardField.CODE) { copy(loyalty = loyalty.copy(format = format)) }

    /** A barcode was read by the camera or from a card photo. */
    fun onBarcodeScanned(code: String, format: BarcodeFormat) =
        update(CardField.CODE) { copy(loyalty = loyalty.copy(code = code, format = format)) }

    /** [duplicateConfirmed]: the user chose "Save anyway" after a [DuplicateWarning]. */
    fun onSave(duplicateConfirmed: Boolean = false) {
        val state = _uiState.value
        if (state.isSaving) return
        val (draft, errors) = buildDraft(state)
        if (draft == null) {
            _uiState.update { it.copy(errors = errors) }
            return
        }
        _uiState.update { it.copy(isSaving = true, errors = emptyMap(), duplicate = null) }
        launchSafely(onError = { onUnexpectedError() }) {
            if (!state.isEditing && !duplicateConfirmed) {
                val duplicate = findDuplicate(cardRepository.observeCards().first(), draft)
                if (duplicate != null) {
                    _uiState.update { it.copy(isSaving = false, duplicate = duplicate) }
                    return@launchSafely
                }
            }
            val result = saveCard(draft)
            _uiState.update {
                when (result) {
                    is SaveCardResult.Saved -> it.copy(isSaving = false, savedCardId = result.id)

                    is SaveCardResult.Invalid -> it.copy(isSaving = false, errors = result.errors)

                    SaveCardResult.AuthenticationRequired ->
                        it.copy(isSaving = false, pendingAuthentication = AuthPurpose.SAVE)

                    SaveCardResult.KeyInvalidated -> it.copy(isSaving = false, error = EditorError.KEY_INVALIDATED)

                    SaveCardResult.Failed -> it.copy(isSaving = false, error = EditorError.UNEXPECTED)
                }
            }
        }
    }

    fun onDuplicateDismissed() = _uiState.update { it.copy(duplicate = null) }

    fun onAuthenticationResult(purpose: AuthPurpose, succeeded: Boolean) {
        _uiState.update { it.copy(pendingAuthentication = null) }
        if (!succeeded) return
        authenticatedHere = true
        when (purpose) {
            AuthPurpose.LOAD -> editingId?.let { launchSafely(onError = { onUnexpectedError() }) { load(it) } }
            AuthPurpose.SAVE -> onSave()
        }
    }

    fun onRetryAuthentication() = _uiState.update { it.copy(pendingAuthentication = AuthPurpose.LOAD) }

    fun onErrorShown() = _uiState.update { it.copy(error = null) }

    private fun onUnexpectedError() =
        _uiState.update { it.copy(isSaving = false, isDownloadingLogo = false, error = EditorError.UNEXPECTED) }

    private suspend fun load(id: CardId) {
        val card = cardRepository.observeCard(id).first()
        if (card == null) {
            _uiState.update { it.copy(loadState = LoadState.NOT_FOUND) }
            return
        }
        if (card.isLocked && !authenticatedHere) {
            _uiState.update {
                it.copy(loadState = LoadState.AUTHENTICATION_REQUIRED, pendingAuthentication = AuthPurpose.LOAD)
            }
            return
        }
        when (val details = cardRepository.readDetails(id)) {
            is SecureResult.Success -> {
                original = card
                _uiState.update { it.populatedFrom(card, details.value, catalogues.countries::country) }
                savedContent = _uiState.value.content
            }

            SecureResult.AuthenticationRequired -> _uiState.update {
                it.copy(loadState = LoadState.AUTHENTICATION_REQUIRED, pendingAuthentication = AuthPurpose.LOAD)
            }

            SecureResult.KeyInvalidated -> _uiState.update { it.copy(loadState = LoadState.KEY_INVALIDATED) }

            is SecureResult.Failed -> _uiState.update { it.copy(loadState = LoadState.FAILED) }
        }
    }

    /** Validates the whole form at once, so that all problems are shown together. */
    private fun buildDraft(state: CardEditorUiState): Pair<CardDraft?, Map<CardField, ValidationError>> {
        val (draft, formErrors) = state.toDraft(original)
        val errors = validator.validate(draft) + formErrors
        return (if (errors.isEmpty()) draft else null) to errors
    }

    private fun updateSide(side: CardSide, image: SideImage) = _uiState.update {
        if (side == CardSide.FRONT) it.copy(front = image) else it.copy(back = image)
    }

    /** Applies [transform] and clears the error previously shown for [field]. */
    private inline fun update(field: CardField, crossinline transform: CardEditorUiState.() -> CardEditorUiState) =
        _uiState.update { it.transform().copy(errors = it.errors - field) }

    private companion object {
        /** Notes are for short remarks, not documents. */
        const val MAX_NOTES_LENGTH = 1_000
        const val MAX_PAN = 19
        const val MAX_CVV = 4
        const val EXPIRY_DIGITS = 4
    }
}
