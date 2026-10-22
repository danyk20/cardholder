package io.github.danyk20.cardholder.core.data.model

import io.github.danyk20.cardholder.core.database.model.CardEntity
import io.github.danyk20.cardholder.core.model.CardValidity
import io.github.danyk20.cardholder.core.model.CountryCode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class CardEntityMapperTest {
    private val ch = requireNotNull(CountryCode.of("CH"))
    private val at = requireNotNull(CountryCode.of("AT"))
    private val de = requireNotNull(CountryCode.of("DE"))

    @Test
    fun `validity is written as sorted codes`() {
        val validity = CardValidity.Countries(setOf(ch, de, at))

        assertEquals("AT,CH,DE", validity.toColumn())
    }

    @Test
    fun `everywhere is written as an empty column`() {
        assertEquals("", CardValidity.Everywhere.toColumn())
    }

    @Test
    fun `an empty column is valid everywhere`() {
        assertEquals(CardValidity.Everywhere, decodeValidity(""))
    }

    @Test
    fun `decoding a column gives its countries`() {
        assertEquals(CardValidity.Countries(setOf(at, ch)), decodeValidity("AT,CH"))
    }

    @Test
    fun `invalid codes are dropped`() {
        assertEquals(CardValidity.Countries(setOf(at)), decodeValidity("AT,XYZ,1A,,"))
    }

    @Test
    fun `lower case codes are dropped as they are not canonical`() {
        assertEquals(CardValidity.Countries(setOf(at)), decodeValidity("AT,ch"))
    }

    @Test
    fun `a column with only invalid codes is valid everywhere`() {
        assertEquals(CardValidity.Everywhere, decodeValidity("xx,ch,nope"))
    }

    @Test
    fun `canonical codes are accepted and malformed ones rejected`() {
        assertEquals(ch, canonicalCountry("CH"))
        assertNull(canonicalCountry("ch"))
        assertNull(canonicalCountry(" CH"))
        assertNull(canonicalCountry("CHE"))
    }

    @Test
    fun `a restricted column round trips through the mapper`() {
        val validity = CardValidity.Countries(setOf(ch, de))

        assertEquals(validity, decodeValidity(validity.toColumn()))
    }

    @Test
    fun `converting an entity carries its validity`() {
        val card = entity(validCountries = "DE,CH").toCard()

        assertEquals(CardValidity.Countries(setOf(ch, de)), card.validity)
    }

    @Test
    fun `an entity without countries is valid everywhere`() {
        assertEquals(CardValidity.Everywhere, entity(validCountries = "").toCard().validity)
    }

    private fun entity(validCountries: String) = CardEntity(
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
        sealedDetails = byteArrayOf(1),
        sealedCvv = null,
        validCountries = validCountries,
    )
}
