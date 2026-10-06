package io.github.danyk20.cardholder.core.testing.data

import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardColor
import io.github.danyk20.cardholder.core.model.CardDetails
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.model.CardNetwork
import io.github.danyk20.cardholder.core.model.CardSides
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.model.ShopRef
import java.time.Instant
import java.time.YearMonth

/** Sample cards for tests. Card numbers are well-known test numbers, never real ones. */
object TestCards {
    private val created: Instant = Instant.parse("2026-01-01T10:00:00Z")

    val visa = Card(
        id = CardId("bank-1"),
        title = "Everyday Visa",
        color = CardColor.BLUE,
        info = CardInfo.Bank(CardNetwork.VISA),
        sides = CardSides.None,
        isLocked = false,
        hasCvv = true,
        createdAt = created,
        updatedAt = created,
    )
    val visaDetails = CardDetails.Bank(number = "4111111111111111", expiry = YearMonth.of(2030, 4), holder = "JANE DOE")
    const val VISA_CVV = "123"

    val idCard = Card(
        id = CardId("id-1"),
        title = "Swiss ID",
        color = CardColor.RED,
        info = CardInfo.Id(CountryCode.of("CH")!!),
        sides = CardSides.None,
        isLocked = true,
        hasCvv = false,
        createdAt = created,
        updatedAt = created,
    )
    val idCardDetails = CardDetails.Id(documentNumber = "C1234567", expiry = null)

    val loyalty = Card(
        id = CardId("loyalty-1"),
        title = "Coffee club",
        color = CardColor.BROWN,
        info = CardInfo.Loyalty(ShopRef.Custom("Corner Coffee"), BarcodeFormat.EAN_13),
        sides = CardSides.None,
        isLocked = false,
        hasCvv = false,
        createdAt = created,
        updatedAt = created,
    )
    val loyaltyDetails = CardDetails.Loyalty(code = "4006381333931")
}
