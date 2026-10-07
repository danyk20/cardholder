package io.github.danyk20.cardholder.core.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * Sensitive, type-specific card data. Always stored encrypted; for locked cards it can only be
 * decrypted after the user authenticated.
 */
sealed interface CardDetails {
    val type: CardType

    /** Free text the user added to the card; protected like the rest of the details. */
    val notes: String

    /** The last day the card is valid, if it has an expiry. */
    val expiresOn: LocalDate?

    data class Bank(val number: String, val expiry: YearMonth, val holder: String, override val notes: String = "") :
        CardDetails {
        override val type = CardType.BANK

        /** Bank cards are valid until the end of the printed month. */
        override val expiresOn: LocalDate get() = expiry.atEndOfMonth()

        override fun toString(): String = "Bank(number=██, expiry=██, holder=██, notes=██)"
    }

    data class Id(val documentNumber: String?, val expiry: LocalDate?, override val notes: String = "") : CardDetails {
        override val type = CardType.ID
        override val expiresOn: LocalDate? get() = expiry

        override fun toString(): String = "Id(documentNumber=██, expiry=██, notes=██)"
    }

    data class Loyalty(val code: String, override val notes: String = "") : CardDetails {
        override val type = CardType.LOYALTY
        override val expiresOn: LocalDate? get() = null

        override fun toString(): String = "Loyalty(code=██, notes=██)"
    }
}
