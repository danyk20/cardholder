package io.github.danyk20.cardholder.feature.cardeditor

import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.domain.validation.CardField
import io.github.danyk20.cardholder.core.domain.validation.ValidationError
import io.github.danyk20.cardholder.core.model.Bank
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.BrandLogo
import io.github.danyk20.cardholder.core.model.BrandRef
import io.github.danyk20.cardholder.core.model.CardColor
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CardNetwork
import io.github.danyk20.cardholder.core.model.CardSide
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.model.ImageRef
import io.github.danyk20.cardholder.core.model.Shop
import java.time.LocalDate

enum class EditorStep {
    /** Choose bank, ID or loyalty card (new cards only). */
    TYPE,

    /** Optionally capture front and back (new cards only). */
    SIDES,

    /** Type-specific details. */
    DETAILS,
}

enum class LoadState {
    LOADING,
    READY,
    AUTHENTICATION_REQUIRED,
    KEY_INVALIDATED,
    NOT_FOUND,

    /** The card couldn't be read for another reason, e.g. damaged data. */
    FAILED,
}

/** An error shown in a dialog after saving failed. */
enum class EditorError {
    KEY_INVALIDATED,
    UNEXPECTED,
}

/** Why the screen asks the user to authenticate. */
enum class AuthPurpose {
    LOAD,
    SAVE,
}

sealed interface SideImage {
    data object None : SideImage

    data class Existing(val ref: ImageRef) : SideImage

    data class New(val uri: String) : SideImage
}

/** An official logo the user can choose for the card of [brandName]. */
data class LogoChoice(val brandName: String, val logo: BrandLogo)

/** The logo shown on a loyalty or bank card. */
sealed interface LogoImage {
    data object None : LogoImage

    data class Existing(val ref: ImageRef) : LogoImage

    /** The shop's official logo, downloaded on request. */
    class Downloaded(val bytes: ByteArray) : LogoImage

    /** A logo the user picked from the gallery. */
    data class Picked(val uri: String) : LogoImage
}

data class BankForm(
    /** Digits only; grouping is a visual transformation. */
    val number: String = "",
    /** Digits only (`MMYY`); the slash is a visual transformation. */
    val expiry: String = "",
    val holder: String = "",
    val cvv: String = "",
    val hasStoredCvv: Boolean = false,
    val removeStoredCvv: Boolean = false,
    /** The issuing bank, from the catalogue or typed by the user. */
    val issuer: BrandRef? = null,
) {
    val network: CardNetwork get() = CardNetwork.detect(number)

    override fun toString(): String = "BankForm(██)"
}

data class IdForm(val country: Country? = null, val documentNumber: String = "", val expiry: LocalDate? = null)

data class LoyaltyForm(
    val shop: BrandRef? = null,
    val code: String = "",
    val format: BarcodeFormat = BarcodeFormat.QR_CODE,
)

data class CardEditorUiState(
    val editingId: CardId? = null,
    val step: EditorStep = EditorStep.TYPE,
    val loadState: LoadState = LoadState.READY,
    val type: CardType = CardType.BANK,
    val title: String = "",
    val color: CardColor = CardColor.Default,
    val front: SideImage = SideImage.None,
    val back: SideImage = SideImage.None,
    val logo: LogoImage = LogoImage.None,
    /** A shop with an official logo was just selected: ask whether to use it, upload one or keep none. */
    val logoChoiceFor: LogoChoice? = null,
    val isDownloadingLogo: Boolean = false,
    val logoDownloadFailed: Boolean = false,
    val bank: BankForm = BankForm(),
    val id: IdForm = IdForm(),
    val loyalty: LoyaltyForm = LoyaltyForm(),
    val isLocked: Boolean = false,
    /** A screen lock exists, so CVVs can be stored and cards locked. */
    val canProtect: Boolean = false,
    val errors: Map<CardField, ValidationError> = emptyMap(),
    val isSaving: Boolean = false,
    val pendingAuthentication: AuthPurpose? = null,
    val savedCardId: CardId? = null,
    val error: EditorError? = null,
    val shops: List<Shop> = emptyList(),
    val banks: List<Bank> = emptyList(),
    val countries: List<Country> = emptyList(),
) {
    val isEditing: Boolean get() = editingId != null

    /** The catalogue shop of a loyalty card, if one is selected. */
    val selectedShop: Shop?
        get() = (loyalty.shop as? BrandRef.Known)?.let { known -> shops.firstOrNull { it.id == known.id } }

    /** The catalogue bank of a bank card, if one is selected. */
    val selectedBank: Bank?
        get() = (bank.issuer as? BrandRef.Known)?.let { known -> banks.firstOrNull { it.id == known.id } }

    /** Official logo of the selected shop or bank, if it has one. */
    val officialLogo: LogoChoice?
        get() = when (type) {
            CardType.LOYALTY -> selectedShop?.let { shop -> shop.logo?.let { LogoChoice(shop.name, it) } }
            CardType.BANK -> selectedBank?.let { bank -> bank.logo?.let { LogoChoice(bank.name, it) } }
            CardType.ID -> null
        }

    /** Suggested title used when the user leaves the title empty. */
    val defaultTitle: String
        get() = when (type) {
            CardType.BANK -> {
                val name = bank.issuer?.name ?: bank.network.displayName
                val last = bank.number.takeLast(LAST_DIGITS)
                if (last.length == LAST_DIGITS) "$name •••• $last" else name
            }

            CardType.ID -> id.country?.name.orEmpty()

            CardType.LOYALTY -> loyalty.shop?.name.orEmpty()
        }

    fun side(side: CardSide): SideImage = if (side == CardSide.FRONT) front else back

    private companion object {
        const val LAST_DIGITS = 4
    }
}
