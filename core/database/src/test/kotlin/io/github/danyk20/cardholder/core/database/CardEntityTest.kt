package io.github.danyk20.cardholder.core.database

import io.github.danyk20.cardholder.core.database.model.CardEntity
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.Test

class CardEntityTest {
    private val card = CardEntity(
        id = "1",
        type = "LOYALTY",
        title = "Coffee",
        color = "BROWN",
        isLocked = false,
        createdAt = 1,
        updatedAt = 2,
        bankNetwork = null,
        idCountry = null,
        loyaltyShopId = null,
        loyaltyShopName = "Corner",
        loyaltyBarcodeFormat = "QR_CODE",
        frontImage = null,
        backImage = null,
        sealedDetails = byteArrayOf(1, 2, 3),
        sealedCvv = null,
    )

    @Test
    fun `cards with different valid countries are not equal`() {
        assertNotEquals(card, card.copy(validCountries = "CH"))
    }

    @Test
    fun `cards with the same valid countries are equal`() {
        val first = card.copy(validCountries = "AT,CH")
        val second = card.copy(validCountries = "AT,CH")

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }
}
