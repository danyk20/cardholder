package io.github.danyk20.cardholder.core.scanning.nfc

import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class EmvCardReaderTest {
    private val ok = "9000"

    /** Builds a TLV with a hex value. */
    private fun tlv(tag: String, valueHex: String): String {
        val length = valueHex.length / 2
        val lengthHex = if (length < 0x80) "%02X".format(length) else "81%02X".format(length)
        return tag + lengthHex + valueHex
    }

    private fun ascii(text: String) = text.encodeToByteArray().toHex()

    /** A fake card answering commands by their hex prefix; everything else is "file not found". */
    private class FakeCard(private val responses: Map<String, String>) {
        val commands = mutableListOf<String>()

        fun transceive(apdu: ByteArray): ByteArray {
            val hex = apdu.toHex()
            commands += hex
            val response = responses.entries.firstOrNull { hex.startsWith(it.key) }?.value ?: "6A82"
            return response.hexToBytes()
        }
    }

    private fun reader(card: FakeCard) = EmvCardReader(card::transceive, today = { LocalDate.of(2026, 10, 6) })

    private val selectPpse = "00A404000E" + ascii("2PAY.SYS.DDF01")
    private val visaAid = "A0000000031010"
    private val ppseResponse = tlv(
        "6F",
        tlv("84", ascii("2PAY.SYS.DDF01")) + tlv("A5", tlv("BF0C", tlv("61", tlv("4F", visaAid) + tlv("87", "01")))),
    )

    @Test
    fun `reads number, expiry and holder from a visa-style gpo response`() {
        val pdol = "9F66049F02069F37045F2A02"
        val card = FakeCard(
            mapOf(
                selectPpse to ppseResponse + ok,
                "00A4040007$visaAid" to
                    tlv("6F", tlv("84", visaAid) + tlv("A5", tlv("50", ascii("VISA")) + tlv("9F38", pdol))) + ok,
                "80A80000" to
                    tlv(
                        "77",
                        tlv("82", "2000") + tlv("57", "4111111111111111D3004201123456789F") +
                            tlv("5F20", ascii("DOE/JANE M")),
                    ) +
                    ok,
            ),
        )

        val data = reader(card).read()!!

        assertEquals("4111111111111111", data.number)
        assertEquals(YearMonth.of(2030, 4), data.expiry)
        assertEquals("JANE M DOE", data.holder)
        // The GPO data object list is filled to exactly the lengths the card asked for (4+6+4+2 bytes).
        val gpo = card.commands.first { it.startsWith("80A8") }
        assertEquals("80A8000012" + "8310", gpo.take(14))
        assertTrue(gpo.contains("F620C000"), "terminal transaction qualifiers are sent")
    }

    @Test
    fun `reads records listed in the AFL for mastercard-style cards`() {
        val mcAid = "A0000000041010"
        val card = FakeCard(
            mapOf(
                selectPpse to tlv("6F", tlv("A5", tlv("BF0C", tlv("61", tlv("4F", mcAid))))) + ok,
                "00A4040007$mcAid" to tlv("6F", tlv("84", mcAid)) + ok,
                // Response format 1: AIP (2 bytes) + AFL: SFI 1, records 1..2
                "80A8000002" to tlv("80", "1980" + "08010200") + ok,
                "00B2010C" to tlv("70", tlv("5F20", ascii("/"))) + ok,
                "00B2020C" to tlv("70", tlv("5A", "5555555555554444") + tlv("5F24", "281231")) + ok,
            ),
        )

        val data = reader(card).read()!!

        assertEquals("5555555555554444", data.number)
        assertEquals(YearMonth.of(2028, 12), data.expiry)
        assertNull(data.holder, "a blank name ('/') is not a name")
    }

    @Test
    fun `falls back to known AIDs when the card has no payment directory`() {
        val card = FakeCard(
            mapOf(
                "00A4040007$visaAid" to tlv("6F", tlv("84", visaAid)) + ok,
                "80A8000002" to tlv("77", tlv("57", "4000056655665556D2912F")) + ok,
            ),
        )

        assertEquals("4000056655665556", reader(card).read()?.number)
    }

    @Test
    fun `follows 61xx and 6Cxx status words`() {
        val card = FakeCard(
            mapOf(
                selectPpse to "6110",
                "00C0000010" to ppseResponse.take(32) + ok,
                // More specific key first: the fake matches by prefix.
                "00A4040007${visaAid}20" to tlv("6F", tlv("84", visaAid)) + ok,
                "00A4040007$visaAid" to "6C20",
                "80A8000002" to tlv("77", tlv("57", "4111111111111111D3004F")) + ok,
            ),
        )

        assertEquals("4111111111111111", reader(card).read()?.number)
    }

    @Test
    fun `a card that keeps asking for resends is given up on`() {
        val card = FakeCard(mapOf("00A4" to "6C10"))

        assertNull(reader(card).read())
    }

    @Test
    fun `non-payment cards and lost connections give no data`() {
        assertNull(reader(FakeCard(emptyMap())).read())
        assertNull(EmvCardReader({ throw IOException("tag lost") }).read())
    }

    @Test
    fun `tlv parser handles multi-byte tags, long lengths and padding`() {
        val parsed = Tlv.parse(
            (
                "00" + tlv("9F38", "9F6604") +
                    tlv("70", tlv("5A", "1234") + tlv("5F20", "AA".repeat(130)))
                ).hexToBytes(),
        )

        assertEquals(listOf(0x9F38, 0x70), parsed.map { it.tag })
        assertEquals(130, parsed.find(0x5F20)?.value?.size)
        assertEquals(listOf(0x9F66 to 4), parseDol("9F6604".hexToBytes()))
    }
}
