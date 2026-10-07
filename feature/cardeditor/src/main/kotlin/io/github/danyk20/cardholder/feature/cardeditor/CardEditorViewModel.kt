package io.github.danyk20.cardholder.feature.cardeditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.danyk20.cardholder.core.domain.model.CardContent
import io.github.danyk20.cardholder.core.domain.model.CardDraft
import io.github.danyk20.cardholder.core.domain.model.CvvChange
import io.github.danyk20.cardholder.core.domain.model.ImageChange
import io.github.danyk20.cardholder.core.domain.model.ImageSource
import io.github.danyk20.cardholder.core.domain.model.SecureResult
import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.domain.repository.LogoDownloader
import io.github.danyk20.cardholder.core.domain.repository.UserPreferencesRepository
import io.github.danyk20.cardholder.core.domain.security.DeviceSecurity
import io.github.danyk20.cardholder.core.domain.usecase.SaveCardResult
import io.github.danyk20.cardholder.core.domain.usecase.SaveCardUseCase
import io.github.danyk20.cardholder.core.domain.validation.BankCardValidator
import io.github.danyk20.cardholder.core.domain.validation.CardDraftValidator
import io.github.danyk20.cardholder.core.domain.validation.CardField
import io.github.danyk20.cardholder.core.domain.validation.ValidationError
import io.github.danyk20.cardholder.core.model.Bank
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.BrandRef
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardColor
import io.github.danyk20.cardholder.core.model.CardDetails
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.model.CardSide
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.model.CountryCode
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
@Suppress("TooManyFunctions")
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
        launchSafely(onError = ::onUnexpectedError) {
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

    private fun CardEditorUiState.prefilledWith(prefill: PhotoPrefill): CardEditorUiState = when (type) {
        CardType.BANK -> bankPrefilled(prefill)
        CardType.ID -> idPrefilled(prefill)
        CardType.LOYALTY -> loyaltyPrefilled(prefill)
    }

    private fun CardEditorUiState.bankPrefilled(prefill: PhotoPrefill) = copy(
        bank = bank.copy(
            number = bank.number.ifEmpty { prefill.number.orEmpty() },
            expiry = bank.expiry.ifEmpty {
                prefill.expiry?.let { "%02d%02d".format(it.monthValue, it.year % CENTURY) }.orEmpty()
            },
            holder = bank.holder.ifEmpty { prefill.holder.orEmpty() },
            // A CVV can only be stored with a screen lock, and never replaces a stored one silently.
            cvv = bank.cvv.ifEmpty { prefill.cvv?.takeIf { canProtect && !bank.hasStoredCvv }.orEmpty() },
            issuer = bank.issuer ?: prefill.bank?.let { BrandRef.Known(it.id, it.name) },
        ),
        logoChoiceFor = logoChoiceFor ?: prefill.bank?.takeIf { bank.issuer == null }
            ?.let { found -> found.logo?.let { LogoChoice(found.name, it) } },
    )

    private fun CardEditorUiState.idPrefilled(prefill: PhotoPrefill) = copy(
        id = id.copy(
            country = id.country ?: prefill.countryCode?.let { code ->
                countries.firstOrNull { it.code.value == code }
            },
            documentNumber = id.documentNumber.ifEmpty { prefill.documentNumber.orEmpty() },
            expiry = id.expiry ?: prefill.idExpiry,
        ),
        title = title.ifEmpty { prefill.holderName.orEmpty() },
    )

    private fun CardEditorUiState.loyaltyPrefilled(prefill: PhotoPrefill): CardEditorUiState {
        val newShop = prefill.shop?.takeIf { loyalty.shop == null }
        val barcode = prefill.barcode?.takeIf { loyalty.code.isEmpty() }
        return copy(
            loyalty = loyalty.copy(
                shop = loyalty.shop ?: newShop?.let { BrandRef.Known(it.id, it.name) },
                code = barcode?.code ?: loyalty.code,
                // A scanned code knows its format; otherwise the shop's usual format is the best guess.
                format = barcode?.format ?: newShop?.takeIf { loyalty.code.isEmpty() }?.defaultFormat ?: loyalty.format,
            ),
            logoChoiceFor = logoChoiceFor ?: newShop?.let { found -> found.logo?.let { LogoChoice(found.name, it) } },
        )
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
                expiry = expiry?.let { "%02d%02d".format(it.monthValue, it.year % CENTURY) } ?: state.bank.expiry,
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
        launchSafely(onError = ::onUnexpectedError) {
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
        launchSafely(onError = ::onUnexpectedError) {
            if (!state.isEditing && !duplicateConfirmed) {
                val duplicate = findDuplicate(draft)
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

    /** Only the plain, non-sensitive columns are compared, so locked cards don't need unlocking. */
    private suspend fun findDuplicate(draft: CardDraft): DuplicateWarning? {
        val cards = cardRepository.observeCards().first()
        val content = draft.content
        val sameBrand = when (content) {
            is CardContent.Loyalty -> cards.firstOrNull { card ->
                (card.info as? CardInfo.Loyalty)?.shop?.let { it.sameBrandAs(content.shop) } == true
            }?.let { DuplicateWarning(DuplicateReason.SAME_SHOP, it.title) }

            is CardContent.Bank -> content.issuer?.let { issuer ->
                cards.firstOrNull { card -> (card.info as? CardInfo.Bank)?.issuer?.sameBrandAs(issuer) == true }
            }?.let { DuplicateWarning(DuplicateReason.SAME_BANK, it.title) }

            is CardContent.Id -> null
        }
        return sameBrand ?: cards.firstOrNull { it.title.trim().equals(draft.title.trim(), ignoreCase = true) }
            ?.let { DuplicateWarning(DuplicateReason.SAME_NAME, it.title) }
    }

    private fun BrandRef.sameBrandAs(other: BrandRef): Boolean = when {
        this is BrandRef.Known && other is BrandRef.Known -> id == other.id
        else -> name.isNotBlank() && name.trim().equals(other.name.trim(), ignoreCase = true)
    }

    fun onAuthenticationResult(purpose: AuthPurpose, succeeded: Boolean) {
        _uiState.update { it.copy(pendingAuthentication = null) }
        if (!succeeded) return
        authenticatedHere = true
        when (purpose) {
            AuthPurpose.LOAD -> editingId?.let { launchSafely(onError = ::onUnexpectedError) { load(it) } }
            AuthPurpose.SAVE -> onSave()
        }
    }

    fun onRetryAuthentication() = _uiState.update { it.copy(pendingAuthentication = AuthPurpose.LOAD) }

    fun onErrorShown() = _uiState.update { it.copy(error = null) }

    private fun onUnexpectedError(@Suppress("UNUSED_PARAMETER") error: Throwable) =
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
                _uiState.update { it.populatedFrom(card, details.value) }
                savedContent = _uiState.value.content
            }

            SecureResult.AuthenticationRequired -> _uiState.update {
                it.copy(loadState = LoadState.AUTHENTICATION_REQUIRED, pendingAuthentication = AuthPurpose.LOAD)
            }

            SecureResult.KeyInvalidated -> _uiState.update { it.copy(loadState = LoadState.KEY_INVALIDATED) }

            is SecureResult.Failed -> _uiState.update { it.copy(loadState = LoadState.FAILED) }
        }
    }

    private fun CardEditorUiState.populatedFrom(card: Card, details: CardDetails): CardEditorUiState {
        val base = copy(
            loadState = LoadState.READY,
            type = card.type,
            title = card.title,
            notes = details.notes,
            color = card.color,
            isLocked = card.isLocked,
            front = card.sides.front?.let(SideImage::Existing) ?: SideImage.None,
            back = card.sides.back?.let(SideImage::Existing) ?: SideImage.None,
            logo = card.logo?.let(LogoImage::Existing) ?: LogoImage.None,
        )
        return when {
            details is CardDetails.Bank -> base.copy(
                bank = BankForm(
                    number = details.number,
                    expiry = "%02d%02d".format(details.expiry.monthValue, details.expiry.year % CENTURY),
                    holder = details.holder,
                    hasStoredCvv = card.hasCvv,
                    issuer = (card.info as? CardInfo.Bank)?.issuer,
                ),
            )

            details is CardDetails.Id && card.info is CardInfo.Id -> base.copy(
                id = IdForm(
                    country = catalogues.countries.country((card.info as CardInfo.Id).country),
                    documentNumber = details.documentNumber.orEmpty(),
                    expiry = details.expiry,
                ),
            )

            details is CardDetails.Loyalty && card.info is CardInfo.Loyalty -> {
                val info = card.info as CardInfo.Loyalty
                base.copy(loyalty = LoyaltyForm(shop = info.shop, code = details.code, format = info.format))
            }

            else -> base
        }
    }

    /**
     * Builds the draft and validates it completely, so that all problems are shown at once. Values the
     * form cannot represent yet (an unparsable expiry, no country or shop) are reported as errors and
     * replaced by placeholders for the remaining validation.
     */
    private fun buildDraft(state: CardEditorUiState): Pair<CardDraft?, Map<CardField, ValidationError>> {
        val formErrors = mutableMapOf<CardField, ValidationError>()
        val content = buildContent(state, formErrors)
        val draft = CardDraft(
            id = state.editingId,
            title = state.title.ifBlank { state.defaultTitle },
            color = state.color,
            content = content,
            front = imageChange(state.front, original?.sides?.front != null),
            back = imageChange(state.back, original?.sides?.back != null),
            logo = if (state.type == CardType.ID) ImageChange.Keep else logoChange(state.logo),
            isLocked = state.isLocked,
        )
        val errors = validator.validate(draft) + formErrors
        return (if (errors.isEmpty()) draft else null) to errors
    }

    private fun buildContent(
        state: CardEditorUiState,
        formErrors: MutableMap<CardField, ValidationError>,
    ): CardContent = when (state.type) {
        CardType.BANK -> CardContent.Bank(
            details = CardDetails.Bank(
                number = state.bank.number,
                expiry = BankCardValidator.parseExpiry(state.bank.expiry) ?: PLACEHOLDER_EXPIRY.also {
                    formErrors[CardField.EXPIRY] =
                        BankCardValidator.validateExpiry(state.bank.expiry) ?: ValidationError.INVALID_DATE
                },
                holder = state.bank.holder,
                notes = state.notes.trim(),
            ),
            cvv = when {
                state.bank.cvv.isNotEmpty() -> CvvChange.Set(state.bank.cvv)
                state.bank.removeStoredCvv -> CvvChange.Remove
                else -> CvvChange.Keep
            },
            issuer = state.bank.issuer,
        )

        CardType.ID -> CardContent.Id(
            country = state.id.country?.code ?: PLACEHOLDER_COUNTRY.also {
                formErrors[CardField.COUNTRY] = ValidationError.REQUIRED
            },
            details = CardDetails.Id(state.id.documentNumber, state.id.expiry, state.notes.trim()),
        )

        CardType.LOYALTY -> CardContent.Loyalty(
            shop = state.loyalty.shop ?: BrandRef.Custom("").also {
                formErrors[CardField.SHOP] = ValidationError.REQUIRED
            },
            format = state.loyalty.format,
            details = CardDetails.Loyalty(state.loyalty.code, state.notes.trim()),
        )
    }

    private fun imageChange(side: SideImage, hadImage: Boolean): ImageChange = when (side) {
        is SideImage.Existing -> ImageChange.Keep
        is SideImage.New -> ImageChange.Replace(ImageSource.Uri(side.uri))
        SideImage.None -> if (hadImage) ImageChange.Remove else ImageChange.Keep
    }

    private fun logoChange(logo: LogoImage): ImageChange = when (logo) {
        is LogoImage.Existing -> ImageChange.Keep
        is LogoImage.Downloaded -> ImageChange.Replace(ImageSource.Bytes(logo.bytes))
        is LogoImage.Picked -> ImageChange.Replace(ImageSource.Uri(logo.uri))
        LogoImage.None -> if (original?.logo != null) ImageChange.Remove else ImageChange.Keep
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
        const val CENTURY = 100
        val PLACEHOLDER_EXPIRY: YearMonth = YearMonth.of(2000, 1)
        val PLACEHOLDER_COUNTRY = CountryCode.of("XX")!!
    }
}
