package io.github.danyk20.cardholder.core.testing.data

import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.BrandRef
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardColor
import io.github.danyk20.cardholder.core.model.CardDetails
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.model.CardNetwork
import io.github.danyk20.cardholder.core.model.CardSides
import io.github.danyk20.cardholder.core.model.CountryCode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * Fictional cards for the Play Store screenshots. No real brands, so no trademarks appear in the
 * store listing; the bank card number belongs to no card network.
 */
object DemoCards {
    private val created: Instant = Instant.parse("2026-09-01T10:00:00Z")

    private fun card(id: String, title: String, color: CardColor, info: CardInfo, hasCvv: Boolean = false) = Card(
        id = CardId(id),
        title = title,
        color = color,
        info = info,
        sides = CardSides.None,
        isLocked = false,
        hasCvv = hasCvv,
        createdAt = created,
        updatedAt = created,
    )

    val grocer = card(
        "demo-grocer",
        "Green Grocer",
        CardColor.GREEN,
        CardInfo.Loyalty(BrandRef.Custom("Green Grocer"), BarcodeFormat.EAN_13),
    )
    val grocerDetails = CardDetails.Loyalty(code = "4006381333931")

    val bank = card(
        "demo-bank",
        "Everyday account",
        CardColor.NAVY,
        CardInfo.Bank(CardNetwork.UNKNOWN, BrandRef.Custom("City Savings Bank")),
        hasCvv = true,
    )
    val bankDetails =
        CardDetails.Bank(number = "9000123456789010", expiry = YearMonth.of(2029, 8), holder = "ALEX MORGAN")

    val bookworm = card(
        "demo-bookworm",
        "Bookworm Club",
        CardColor.PURPLE,
        CardInfo.Loyalty(BrandRef.Custom("Bookworm Club"), BarcodeFormat.QR_CODE),
    )
    val bookwormDetails = CardDetails.Loyalty(code = "BWC-2048-7731-55")

    val identity = card("demo-id", "Identity card", CardColor.RED, CardInfo.Id(CountryCode.of("CH")!!))
    val identityDetails = CardDetails.Id(documentNumber = "C4X9P2L7", expiry = LocalDate.of(2031, 5, 14))

    val coffee = card(
        "demo-coffee",
        "Corner Coffee",
        CardColor.AMBER,
        CardInfo.Loyalty(BrandRef.Custom("Corner Coffee"), BarcodeFormat.CODE_128),
    )
    val coffeeDetails = CardDetails.Loyalty(code = "CC00451287")

    /** All demo cards with their details, in display order. */
    val all: List<Pair<Card, CardDetails>> = listOf(
        grocer to grocerDetails,
        bank to bankDetails,
        bookworm to bookwormDetails,
        identity to identityDetails,
        coffee to coffeeDetails,
    )
}
