package io.github.danyk20.cardholder.core.domain.validation

import io.github.danyk20.cardholder.core.model.BarcodeFormat
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class BarcodeValidatorTest {
    private fun validate(code: String, format: BarcodeFormat) = BarcodeValidator.validate(code, format)

    @Test
    fun `empty code is required for every format`() {
        BarcodeFormat.entries.forEach { assertEquals(ValidationError.REQUIRED, validate("", it), it.name) }
    }

    @Test
    fun `ean and upc accept codes with or without check digit`() {
        assertNull(validate("4006381333931", BarcodeFormat.EAN_13))
        assertNull(validate("400638133393", BarcodeFormat.EAN_13))
        assertNull(validate("96385074", BarcodeFormat.EAN_8))
        assertNull(validate("036000291452", BarcodeFormat.UPC_A))
        assertEquals(ValidationError.INVALID_CHECKSUM, validate("4006381333932", BarcodeFormat.EAN_13))
        assertEquals(ValidationError.INVALID_LENGTH, validate("123", BarcodeFormat.EAN_13))
        assertEquals(ValidationError.INVALID_CHARACTERS, validate("40063813339A", BarcodeFormat.EAN_13))
    }

    @Test
    fun `upc-e`() {
        assertNull(validate("01234565", BarcodeFormat.UPC_E))
        assertEquals(ValidationError.INVALID_LENGTH, validate("0123", BarcodeFormat.UPC_E))
        assertEquals(ValidationError.INVALID_CHARACTERS, validate("91234565", BarcodeFormat.UPC_E))
    }

    @Test
    fun `itf requires an even number of digits`() {
        assertNull(validate("1234", BarcodeFormat.ITF))
        assertEquals(ValidationError.INVALID_LENGTH, validate("123", BarcodeFormat.ITF))
        assertEquals(ValidationError.INVALID_CHARACTERS, validate("12A4", BarcodeFormat.ITF))
    }

    @Test
    fun `code 39 charset`() {
        assertNull(validate("ABC-123 $/+%", BarcodeFormat.CODE_39))
        assertNull(validate("abc123", BarcodeFormat.CODE_39))
        assertEquals(ValidationError.INVALID_CHARACTERS, validate("AB_C", BarcodeFormat.CODE_39))
    }

    @Test
    fun `codabar with and without guards`() {
        assertNull(validate("A40156B", BarcodeFormat.CODABAR))
        assertNull(validate("40156", BarcodeFormat.CODABAR))
        assertEquals(ValidationError.REQUIRED, validate("AB", BarcodeFormat.CODABAR))
        assertEquals(ValidationError.INVALID_CHARACTERS, validate("40X56", BarcodeFormat.CODABAR))
    }

    @Test
    fun `code 128 accepts printable ascii only`() {
        assertNull(validate("Hello World 123!", BarcodeFormat.CODE_128))
        assertEquals(ValidationError.INVALID_CHARACTERS, validate("Grüezi", BarcodeFormat.CODE_128))
        assertEquals(ValidationError.INVALID_LENGTH, validate("1".repeat(81), BarcodeFormat.CODE_128))
    }

    @Test
    fun `two dimensional formats accept any text`() {
        assertNull(validate("https://example.com/?q=Grüezi", BarcodeFormat.QR_CODE))
        assertEquals(ValidationError.INVALID_LENGTH, validate("x".repeat(2001), BarcodeFormat.AZTEC))
    }
}
