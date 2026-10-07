package io.github.danyk20.cardholder.core.model

import java.time.Instant
import java.time.LocalDate

/**
 * A stored card with its **non-sensitive** information.
 *
 * Everything in this class can be shown in the card list without authentication. Sensitive
 * values live in [CardDetails] (and the CVV of a bank card), which are read separately and may
 * require the user to authenticate when the card [isLocked].
 */
data class Card(
    val id: CardId,
    val title: String,
    val color: CardColor,
    val info: CardInfo,
    val sides: CardSides,
    /** Logo shown on the card face, e.g. the shop's logo of a loyalty card. */
    val logo: ImageRef? = null,
    val isLocked: Boolean,
    val hasCvv: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Place in the user's custom order; lower comes first. */
    val position: Int = 0,
    /** Shown first in the list, in app shortcuts and in the widget. */
    val isFavourite: Boolean = false,
    /** How often the card was used (its code shown or its details opened), for the "most used" order. */
    val useCount: Int = 0,
    val lastUsedAt: Instant? = null,
    /**
     * The last day the card is valid. Kept outside the encrypted details so expiry reminders work
     * without unlocking the card; treated like the title, which is also shown without unlocking.
     */
    val expiresOn: LocalDate? = null,
) {
    val type: CardType get() = info.type
}

/** Type-specific information that is safe to display without authentication. */
sealed interface CardInfo {
    val type: CardType

    data class Bank(
        val network: CardNetwork,
        /** The issuing bank, if the user chose one. */
        val issuer: BrandRef? = null,
    ) : CardInfo {
        override val type = CardType.BANK
    }

    data class Id(val country: CountryCode) : CardInfo {
        override val type = CardType.ID
    }

    data class Loyalty(val shop: BrandRef, val format: BarcodeFormat) : CardInfo {
        override val type = CardType.LOYALTY
    }
}
