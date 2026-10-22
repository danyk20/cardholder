package io.github.danyk20.cardholder.feature.cardeditor

import io.github.danyk20.cardholder.core.domain.model.CardContent
import io.github.danyk20.cardholder.core.domain.model.CardDraft
import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.domain.model.CvvChange
import io.github.danyk20.cardholder.core.domain.model.ImageChange
import io.github.danyk20.cardholder.core.domain.model.ImageSource
import io.github.danyk20.cardholder.core.domain.validation.BankCardValidator
import io.github.danyk20.cardholder.core.domain.validation.CardField
import io.github.danyk20.cardholder.core.domain.validation.ValidationError
import io.github.danyk20.cardholder.core.model.BrandRef
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardDetails
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.model.CountryCode
import java.time.YearMonth

// Conversions between a stored card and the editor's form state.

private const val CENTURY = 100
private val PLACEHOLDER_EXPIRY: YearMonth = YearMonth.of(2000, 1)
private val PLACEHOLDER_COUNTRY = CountryCode.of("XX")!!

/** The expiry as the form's four digits, `MMYY`. */
internal fun YearMonth.toExpiryDigits(): String = "%02d%02d".format(monthValue, year % CENTURY)

/** Fills the form with a loaded [card]; [country] resolves an ID card's country for the picker. */
internal fun CardEditorUiState.populatedFrom(
    card: Card,
    details: CardDetails,
    country: (CountryCode) -> Country?,
): CardEditorUiState {
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
        validity = card.validity,
    )
    return when {
        details is CardDetails.Bank -> base.copy(
            bank = BankForm(
                number = details.number,
                expiry = details.expiry.toExpiryDigits(),
                holder = details.holder,
                hasStoredCvv = card.hasCvv,
                issuer = (card.info as? CardInfo.Bank)?.issuer,
            ),
        )

        details is CardDetails.Id && card.info is CardInfo.Id -> base.copy(
            id = IdForm(
                country = country((card.info as CardInfo.Id).country),
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
 * Builds the draft for saving, plus the errors of values the form cannot represent yet (an unparsable
 * expiry, no country or shop). Those are replaced by placeholders, so the draft can still be validated
 * completely and all problems are shown at once.
 */
internal fun CardEditorUiState.toDraft(original: Card?): Pair<CardDraft, Map<CardField, ValidationError>> {
    val formErrors = mutableMapOf<CardField, ValidationError>()
    val content = buildContent(this, formErrors)
    val draft = CardDraft(
        id = editingId,
        title = title.ifBlank { defaultTitle },
        color = color,
        content = content,
        front = imageChange(front, original?.sides?.front != null),
        back = imageChange(back, original?.sides?.back != null),
        logo = if (type == CardType.ID) ImageChange.Keep else logoChange(logo, hadLogo = original?.logo != null),
        isLocked = isLocked,
        validity = validity,
    )
    return draft to formErrors
}

private fun buildContent(state: CardEditorUiState, formErrors: MutableMap<CardField, ValidationError>): CardContent =
    when (state.type) {
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

private fun logoChange(logo: LogoImage, hadLogo: Boolean): ImageChange = when (logo) {
    is LogoImage.Existing -> ImageChange.Keep
    is LogoImage.Downloaded -> ImageChange.Replace(ImageSource.Bytes(logo.bytes))
    is LogoImage.Picked -> ImageChange.Replace(ImageSource.Uri(logo.uri))
    LogoImage.None -> if (hadLogo) ImageChange.Remove else ImageChange.Keep
}
