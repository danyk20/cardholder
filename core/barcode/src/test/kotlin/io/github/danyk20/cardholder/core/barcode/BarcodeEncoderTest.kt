package io.github.danyk20.cardholder.core.barcode

import io.github.danyk20.cardholder.core.model.BarcodeFormat
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class BarcodeEncoderTest {
    private val samples = mapOf(
        BarcodeFormat.QR_CODE to "https://example.com/member/12345?name=Grüezi",
        BarcodeFormat.AZTEC to "MEMBER-12345",
        BarcodeFormat.DATA_MATRIX to "MEMBER-12345",
        BarcodeFormat.PDF_417 to "MEMBER-12345",
        BarcodeFormat.EAN_13 to "4006381333931",
        BarcodeFormat.EAN_8 to "96385074",
        BarcodeFormat.UPC_A to "036000291452",
        BarcodeFormat.UPC_E to "01234565",
        BarcodeFormat.CODE_128 to "Member 12345",
        BarcodeFormat.CODE_39 to "MEMBER-12345",
        BarcodeFormat.CODE_93 to "MEMBER-12345",
        BarcodeFormat.ITF to "12345678",
        BarcodeFormat.CODABAR to "A40156B",
    )

    @Test
    fun `every supported format can be encoded`() {
        assertTrue(samples.keys.containsAll(BarcodeFormat.entries))
        samples.forEach { (format, content) ->
            val matrix = assertNotNull(BarcodeEncoder.encode(content, format, 600, 300), format.name)
            assertTrue(matrix.width >= 100, "${format.name} width ${matrix.width}")
        }
    }

    @Test
    fun `ean-13 without check digit is completed`() {
        assertNotNull(BarcodeEncoder.encode("400638133393", BarcodeFormat.EAN_13, 600, 200))
    }

    @Test
    fun `content that does not fit the format returns null`() {
        assertNull(BarcodeEncoder.encode("not-digits", BarcodeFormat.EAN_13, 600, 200))
        assertNull(BarcodeEncoder.encode("123", BarcodeFormat.ITF, 600, 200))
    }
}
