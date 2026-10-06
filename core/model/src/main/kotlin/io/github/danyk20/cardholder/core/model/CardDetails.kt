package io.github.danyk20.cardholder.core.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * Sensitive, type-specific card data. Always stored encrypted; for locked cards it can only be
 * decrypted after the user authenticated.
 */
sealed interface CardDetails {
    val type: CardType

    data class Bank(
        val number: String,
        val expiry: YearMonth,
        val holder: String,
    ) : CardDetails {
        override val type = CardType.BANK

        override fun toString(): String = "Bank(number=██, expiry=██, holder=██)"
    }

    data class Id(
        val documentNumber: String?,
        val expiry: LocalDate?,
    ) : CardDetails {
        override val type = CardType.ID

        override fun toString(): String = "Id(documentNumber=██, expiry=██)"
    }

    data class Loyalty(
        val code: String,
    ) : CardDetails {
        override val type = CardType.LOYALTY

        override fun toString(): String = "Loyalty(code=██)"
    }
}
