package io.github.danyk20.cardholder.core.domain.validation

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ChecksumsTest {
    @Test
    fun `luhn accepts valid numbers`() {
        listOf("4111111111111111", "378282246310005", "79927398713").forEach {
            assertTrue(Checksums.isLuhnValid(it), it)
        }
    }

    @Test
    fun `luhn rejects invalid numbers`() {
        listOf("4111111111111112", "79927398710", "").forEach { assertFalse(Checksums.isLuhnValid(it), it) }
    }

    @Test
    fun `gtin check digit`() {
        assertEquals(1, Checksums.gtinCheckDigit("400638133393"))
        assertTrue(Checksums.isGtinValid("4006381333931"))
        assertTrue(Checksums.isGtinValid("96385074"))
        assertTrue(Checksums.isGtinValid("036000291452"))
        assertFalse(Checksums.isGtinValid("4006381333932"))
        assertFalse(Checksums.isGtinValid("4"))
    }
}
