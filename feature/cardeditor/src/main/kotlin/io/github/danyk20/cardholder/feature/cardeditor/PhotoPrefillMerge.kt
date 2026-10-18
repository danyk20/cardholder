package io.github.danyk20.cardholder.feature.cardeditor

import io.github.danyk20.cardholder.core.model.BrandRef
import io.github.danyk20.cardholder.core.model.CardType

/** Fills empty fields with what was read from the card photos; never overwrites the user's input. */
internal fun CardEditorUiState.prefilledWith(prefill: PhotoPrefill): CardEditorUiState = when (type) {
    CardType.BANK -> bankPrefilled(prefill)
    CardType.ID -> idPrefilled(prefill)
    CardType.LOYALTY -> loyaltyPrefilled(prefill)
}

private fun CardEditorUiState.bankPrefilled(prefill: PhotoPrefill) = copy(
    bank = bank.copy(
        number = bank.number.ifEmpty { prefill.number.orEmpty() },
        expiry = bank.expiry.ifEmpty {
            prefill.expiry?.let { it.toExpiryDigits() }.orEmpty()
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
