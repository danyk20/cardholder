package io.github.danyk20.cardholder.core.domain.scan

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class CardTextParserTest {
    @Test
    fun `reads number, expiry, holder and cvv of a bank card`() {
        val front = listOf("VISA", "Debit", "4111 1111 1111 1111", "VALID FROM 05/21", "VALID THRU 08/29", "JANE DOE")
        val back = listOf("AUTHORIZED SIGNATURE", "1111 123", "Customer service +44 20 1234 5678")

        val card = CardTextParser.parseBank(front, back)

        assertEquals("4111111111111111", card.number)
        assertEquals(YearMonth.of(2029, 8), card.expiry)
        assertEquals("JANE DOE", card.holder)
        assertEquals("123", card.cvv)
    }

    @Test
    fun `ignores numbers that fail the check digit and ambiguous cvvs`() {
        val card = CardTextParser.parseBank(
            front = listOf("4111 1111 1111 1112", "CITY SAVINGS BANK", "12/30"),
            back = listOf("Call 0800 123 456", "Code 789"),
            bankNames = listOf("City Savings Bank"),
        )

        assertNull(card.number)
        assertNull(card.holder) // the bank name isn't a person
        assertNull(card.cvv)
        assertEquals(YearMonth.of(2030, 12), card.expiry)
    }

    @Test
    fun `reads the cvv next to its label`() {
        val card = CardTextParser.parseBank(listOf("5555 5555 5555 4444"), listOf("CVC 321", "Valid 01/30"))

        assertEquals("321", card.cvv)
    }

    @Test
    fun `reads a German ID card's machine-readable zone`() {
        val mrz = listOf(
            "IDD<<T220001293<<<<<<<<<<<<<<<",
            "6408125<2010315D<<<<<<<<<<<<<4",
            "MUSTERMANN<<ERIKA<<<<<<<<<<<<<",
        )

        val id = CardTextParser.parseMrz(listOf("BUNDESREPUBLIK DEUTSCHLAND") + mrz)

        assertEquals(IdDocumentText("T22000129", LocalDate.of(2020, 10, 31), "DE", "Erika Mustermann"), id)
    }

    @Test
    fun `reads a passport and tolerates OCR spacing and lookalike digits`() {
        val mrz = listOf(
            "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
            // "O" read instead of "0" in the expiry date, plus stray spaces.
            "L898902C36UTO7408122F12O4159ZE184226B<<<<< 10",
        )

        val passport = CardTextParser.parseMrz(mrz)

        assertEquals("L898902C3", passport?.documentNumber)
        assertEquals(LocalDate.of(2012, 4, 15), passport?.expiry)
        assertNull(passport?.issuingCountry) // "UTO" is the ICAO test country
        assertEquals("Anna Maria Eriksson", passport?.holderName)
    }

    @Test
    fun `drops a document number whose check digit doesn't match`() {
        val mrz = listOf(
            "IDD<<T220001294<<<<<<<<<<<<<<<",
            "6408125<2010315D<<<<<<<<<<<<<4",
            "MUSTERMANN<<ERIKA<<<<<<<<<<<<<",
        )

        assertNull(CardTextParser.parseMrz(mrz)?.documentNumber)
    }

    @Test
    fun `finds the shop or bank printed on the card`() {
        val names = listOf("Lidl", "Kaufland", "dm", "Raiffeisen Bank", "Tesco")

        assertEquals("Kaufland", CardTextParser.findBrand(listOf("KAUFLAND Card", "Nr. 1234"), names))
        assertEquals("Raiffeisen Bank", CardTextParser.findBrand(listOf("raiffeisen bank", "Debit"), names))
        assertNull(CardTextParser.findBrand(listOf("Lidlová 5"), names)) // not a whole word
        assertNull(CardTextParser.findBrand(listOf("DM"), names)) // too short to trust
    }
}
