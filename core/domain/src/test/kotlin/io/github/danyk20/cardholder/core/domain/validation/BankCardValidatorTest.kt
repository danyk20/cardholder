package io.github.danyk20.cardholder.core.domain.validation

import io.github.danyk20.cardholder.core.model.CardNetwork
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class BankCardValidatorTest {
    @Test
    fun `number validation`() {
        assertNull(BankCardValidator.validateNumber("4111 1111 1111 1111"))
        assertNull(BankCardValidator.validateNumber("4111-1111-1111-1111"))
        assertEquals(ValidationError.REQUIRED, BankCardValidator.validateNumber("  "))
        assertEquals(ValidationError.INVALID_CHARACTERS, BankCardValidator.validateNumber("4111a111"))
        assertEquals(ValidationError.INVALID_LENGTH, BankCardValidator.validateNumber("4111"))
        assertEquals(ValidationError.INVALID_LENGTH, BankCardValidator.validateNumber("4".repeat(20)))
        assertEquals(ValidationError.INVALID_CHECKSUM, BankCardValidator.validateNumber("4111111111111112"))
    }

    @Test
    fun `parses expiry in common formats`() {
        assertEquals(YearMonth.of(2027, 4), BankCardValidator.parseExpiry("04/27"))
        assertEquals(YearMonth.of(2027, 4), BankCardValidator.parseExpiry("0427"))
        assertEquals(YearMonth.of(2031, 12), BankCardValidator.parseExpiry("12/2031"))
        assertNull(BankCardValidator.parseExpiry("13/27"))
        assertNull(BankCardValidator.parseExpiry("00/27"))
        assertNull(BankCardValidator.parseExpiry("4/7"))
    }

    @Test
    fun `expiry validation`() {
        assertNull(BankCardValidator.validateExpiry("01/30"))
        assertEquals(ValidationError.REQUIRED, BankCardValidator.validateExpiry(""))
        assertEquals(ValidationError.INVALID_DATE, BankCardValidator.validateExpiry("99/99"))
    }

    @Test
    fun `detects expired cards`() {
        val today = YearMonth.of(2026, 10)
        assertTrue(BankCardValidator.isExpired(YearMonth.of(2026, 9), today))
        assertFalse(BankCardValidator.isExpired(YearMonth.of(2026, 10), today))
    }

    @Test
    fun `holder is required`() {
        assertEquals(ValidationError.REQUIRED, BankCardValidator.validateHolder(" "))
        assertNull(BankCardValidator.validateHolder("Jane Doe"))
    }

    @Test
    fun `cvv validation depends on network`() {
        assertNull(BankCardValidator.validateCvv("", CardNetwork.VISA))
        assertNull(BankCardValidator.validateCvv("123", CardNetwork.VISA))
        assertNull(BankCardValidator.validateCvv("1234", CardNetwork.AMERICAN_EXPRESS))
        assertNull(BankCardValidator.validateCvv("1234", CardNetwork.UNKNOWN))
        assertEquals(ValidationError.INVALID_LENGTH, BankCardValidator.validateCvv("1234", CardNetwork.VISA))
        assertEquals(ValidationError.INVALID_LENGTH, BankCardValidator.validateCvv("123", CardNetwork.AMERICAN_EXPRESS))
        assertEquals(ValidationError.INVALID_CHARACTERS, BankCardValidator.validateCvv("12a", CardNetwork.VISA))
    }

    @Test
    fun `number status while typing`() {
        assertEquals(InputStatus.INCOMPLETE, BankCardValidator.numberStatus(""))
        assertEquals(InputStatus.INCOMPLETE, BankCardValidator.numberStatus("4111 1111"))
        assertEquals(InputStatus.VALID, BankCardValidator.numberStatus("4111 1111 1111 1111"))
        // A typo is reported as soon as the number is complete.
        assertEquals(InputStatus.INVALID, BankCardValidator.numberStatus("4111 1111 1111 1112"))
        assertEquals(InputStatus.VALID, BankCardValidator.numberStatus("3782 822463 10005")) // Amex, 15 digits
        assertEquals(InputStatus.INVALID, BankCardValidator.numberStatus("3782 822463 10006"))
        assertEquals(InputStatus.INVALID, BankCardValidator.numberStatus("4111-11x"))
    }

    @Test
    fun `expiry status while typing`() {
        assertEquals(InputStatus.INCOMPLETE, BankCardValidator.expiryStatus("08"))
        assertEquals(InputStatus.VALID, BankCardValidator.expiryStatus("0829"))
        assertEquals(InputStatus.INVALID, BankCardValidator.expiryStatus("1329"))
    }
}
