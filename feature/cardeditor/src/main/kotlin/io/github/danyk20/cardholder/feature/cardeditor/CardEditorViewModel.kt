package io.github.danyk20.cardholder.feature.cardeditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.danyk20.cardholder.core.domain.model.CardContent
import io.github.danyk20.cardholder.core.domain.model.CardDraft
import io.github.danyk20.cardholder.core.domain.model.CvvChange
import io.github.danyk20.cardholder.core.domain.model.ImageChange
import io.github.danyk20.cardholder.core.domain.model.ImageSource
import io.github.danyk20.cardholder.core.domain.model.SecureResult
import io.github.danyk20.cardholder.core.domain.repository.BarcodeImageScanner
import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.domain.repository.CountryRepository
import io.github.danyk20.cardholder.core.domain.repository.LogoDownloader
import io.github.danyk20.cardholder.core.domain.repository.ShopRepository
import io.github.danyk20.cardholder.core.domain.security.DeviceSecurity
import io.github.danyk20.cardholder.core.domain.usecase.SaveCardResult
import io.github.danyk20.cardholder.core.domain.usecase.SaveCardUseCase
import io.github.danyk20.cardholder.core.domain.validation.BankCardValidator
import io.github.danyk20.cardholder.core.domain.validation.CardDraftValidator
import io.github.danyk20.cardholder.core.domain.validation.CardField
import io.github.danyk20.cardholder.core.domain.validation.ValidationError
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardColor
import io.github.danyk20.cardholder.core.model.CardDetails
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.model.CardSide
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.model.Shop
import io.github.danyk20.cardholder.core.model.ShopRef
import io.github.danyk20.cardholder.feature.cardeditor.navigation.CardEditorDestination
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
@Suppress("TooManyFunctions")
class CardEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cardRepository: CardRepository,
    private val saveCard: SaveCardUseCase,
    private val shopRepository: ShopRepository,
    private val countryRepository: CountryRepository,
    private val deviceSecurity: DeviceSecurity,
    private val validator: CardDraftValidator,
    private val barcodeImageScanner: BarcodeImageScanner,
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

    init {
        viewModelScope.launch {
            val shops = shopRepository.shops()
            _uiState.update { it.copy(shops = shops, countries = countryRepository.countries()) }
            if (editingId != null) load(editingId)
        }
    }

    /** Re-checks the screen lock, e.g. after the user returns from the system settings. */
    fun onResume() = _uiState.update { it.copy(canProtect = deviceSecurity.isDeviceSecure()) }

    fun onTypeSelected(type: CardType) = _uiState.update { it.copy(type = type, step = EditorStep.SIDES) }

    fun onSidesDone() = _uiState.update { it.copy(step = EditorStep.DETAILS) }

    /** Returns `false` when there is no previous step and the screen should close. */
    fun onBack(): Boolean {
        val state = _uiState.value
        if (state.isEditing || state.step == EditorStep.TYPE) return false
        _uiState.update { it.copy(step = EditorStep.entries[state.step.ordinal - 1]) }
        return true
    }

    fun onSideImagePicked(side: CardSide, uri: String) {
        updateSide(side, SideImage.New(uri))
        if (_uiState.value.type == CardType.LOYALTY && _uiState.value.loyalty.code.isEmpty()) {
            // Loyalty cards usually carry their code on the back: offer it without typing.
            viewModelScope.launch {
                barcodeImageScanner.scan(uri)?.let { barcode ->
                    if (_uiState.value.loyalty.code.isEmpty()) onBarcodeScanned(barcode.code, barcode.format)
                }
            }
        }
    }

    fun onSideImageRemoved(side: CardSide) = updateSide(side, SideImage.None)

    fun onTitleChange(title: String) = update(CardField.TITLE) { copy(title = title) }

    fun onColorChange(color: CardColor) = _uiState.update { it.copy(color = color) }

    fun onLockedChange(locked: Boolean) = _uiState.update { it.copy(isLocked = locked && it.canProtect) }

    fun onNumberChange(number: String) =
        update(CardField.NUMBER) { copy(bank = bank.copy(number = number.filter(Char::isDigit).take(MAX_PAN))) }

    fun onExpiryChange(expiry: String) =
        update(CardField.EXPIRY) { copy(bank = bank.copy(expiry = expiry.filter(Char::isDigit).take(EXPIRY_DIGITS))) }

    fun onHolderChange(holder: String) = update(CardField.HOLDER) { copy(bank = bank.copy(holder = holder)) }

    fun onCvvChange(cvv: String) =
        update(CardField.CVV) { copy(bank = bank.copy(cvv = cvv.filter(Char::isDigit).take(MAX_CVV))) }

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
                shop = ShopRef.Known(shop.id, shop.name),
                format = if (loyalty.code.isEmpty()) shop.defaultFormat else loyalty.format,
            ),
            logoChoiceFor = shop.takeIf { it.logo != null },
        )
    }

    /** Downloads the selected shop's official logo; the app's only network request. */
    fun onUseOfficialLogo() {
        val logo = (_uiState.value.logoChoiceFor ?: _uiState.value.selectedShop)?.logo ?: return
        _uiState.update { it.copy(logoChoiceFor = null, isDownloadingLogo = true, logoDownloadFailed = false) }
        viewModelScope.launch {
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
        update(CardField.SHOP) { copy(loyalty = loyalty.copy(shop = ShopRef.Custom(name.trim()))) }

    fun onCodeChange(code: String) = update(CardField.CODE) { copy(loyalty = loyalty.copy(code = code)) }

    fun onFormatChange(format: BarcodeFormat) = update(CardField.CODE) { copy(loyalty = loyalty.copy(format = format)) }

    /** A barcode was read by the camera or from a card photo. */
    fun onBarcodeScanned(code: String, format: BarcodeFormat) =
        update(CardField.CODE) { copy(loyalty = loyalty.copy(code = code, format = format)) }

    fun onSave() {
        val state = _uiState.value
        if (state.isSaving) return
        val (draft, errors) = buildDraft(state)
        if (draft == null) {
            _uiState.update { it.copy(errors = errors) }
            return
        }
        _uiState.update { it.copy(isSaving = true, errors = emptyMap()) }
        viewModelScope.launch {
            val result = saveCard(draft)
            _uiState.update {
                when (result) {
                    is SaveCardResult.Saved -> it.copy(isSaving = false, savedCardId = result.id)

                    is SaveCardResult.Invalid -> it.copy(isSaving = false, errors = result.errors)

                    SaveCardResult.AuthenticationRequired ->
                        it.copy(isSaving = false, pendingAuthentication = AuthPurpose.SAVE)

                    SaveCardResult.KeyInvalidated -> it.copy(isSaving = false, showKeyInvalidatedError = true)
                }
            }
        }
    }

    fun onAuthenticationResult(purpose: AuthPurpose, succeeded: Boolean) {
        _uiState.update { it.copy(pendingAuthentication = null) }
        if (!succeeded) return
        when (purpose) {
            AuthPurpose.LOAD -> editingId?.let { viewModelScope.launch { load(it) } }
            AuthPurpose.SAVE -> onSave()
        }
    }

    fun onRetryAuthentication() = _uiState.update { it.copy(pendingAuthentication = AuthPurpose.LOAD) }

    fun onKeyInvalidatedErrorShown() = _uiState.update { it.copy(showKeyInvalidatedError = false) }

    private suspend fun load(id: CardId) {
        val card = cardRepository.observeCard(id).first()
        if (card == null) {
            _uiState.update { it.copy(loadState = LoadState.NOT_FOUND) }
            return
        }
        when (val details = cardRepository.readDetails(id)) {
            is SecureResult.Success -> {
                original = card
                _uiState.update { it.populatedFrom(card, details.value) }
            }

            SecureResult.AuthenticationRequired -> _uiState.update {
                it.copy(loadState = LoadState.AUTHENTICATION_REQUIRED, pendingAuthentication = AuthPurpose.LOAD)
            }

            SecureResult.KeyInvalidated -> _uiState.update { it.copy(loadState = LoadState.KEY_INVALIDATED) }
        }
    }

    private fun CardEditorUiState.populatedFrom(card: Card, details: CardDetails): CardEditorUiState {
        val base = copy(
            loadState = LoadState.READY,
            type = card.type,
            title = card.title,
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
                ),
            )

            details is CardDetails.Id && card.info is CardInfo.Id -> base.copy(
                id = IdForm(
                    country = countryRepository.country((card.info as CardInfo.Id).country),
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
            logo = if (state.type == CardType.LOYALTY) logoChange(state.logo) else ImageChange.Keep,
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
            ),
            cvv = when {
                state.bank.cvv.isNotEmpty() -> CvvChange.Set(state.bank.cvv)
                state.bank.removeStoredCvv -> CvvChange.Remove
                else -> CvvChange.Keep
            },
        )

        CardType.ID -> CardContent.Id(
            country = state.id.country?.code ?: PLACEHOLDER_COUNTRY.also {
                formErrors[CardField.COUNTRY] = ValidationError.REQUIRED
            },
            details = CardDetails.Id(state.id.documentNumber, state.id.expiry),
        )

        CardType.LOYALTY -> CardContent.Loyalty(
            shop = state.loyalty.shop ?: ShopRef.Custom("").also {
                formErrors[CardField.SHOP] = ValidationError.REQUIRED
            },
            format = state.loyalty.format,
            details = CardDetails.Loyalty(state.loyalty.code),
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
        const val MAX_PAN = 19
        const val MAX_CVV = 4
        const val EXPIRY_DIGITS = 4
        const val CENTURY = 100
        val PLACEHOLDER_EXPIRY: YearMonth = YearMonth.of(2000, 1)
        val PLACEHOLDER_COUNTRY = CountryCode.of("XX")!!
    }
}
