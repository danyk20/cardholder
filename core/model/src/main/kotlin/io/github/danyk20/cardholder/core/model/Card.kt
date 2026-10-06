package io.github.danyk20.cardholder.core.model

import java.time.Instant

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
) {
    val type: CardType get() = info.type
}

/** Type-specific information that is safe to display without authentication. */
sealed interface CardInfo {
    val type: CardType

    data class Bank(val network: CardNetwork) : CardInfo {
        override val type = CardType.BANK
    }

    data class Id(val country: CountryCode) : CardInfo {
        override val type = CardType.ID
    }

    data class Loyalty(val shop: ShopRef, val format: BarcodeFormat) : CardInfo {
        override val type = CardType.LOYALTY
    }
}
