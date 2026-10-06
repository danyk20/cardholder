package io.github.danyk20.cardholder.core.scanning.nfc

import java.io.IOException
import java.security.SecureRandom
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Data a contactless payment card reveals. The CVV is never stored on the chip. */
data class EmvCardData(
    val number: String,
    val expiry: YearMonth?,
    /** Often withheld by modern cards for privacy; `null` then. */
    val holder: String?,
) {
    override fun toString(): String = "EmvCardData(██)"
}

/**
 * Reads the card number, expiry and (if present) holder name from an EMV contactless card, using only
 * the commands a payment terminal sends before a transaction: no transaction is ever started.
 *
 * [transceive] sends one APDU and returns the card's response including the status word.
 */
class EmvCardReader(
    private val transceive: (ByteArray) -> ByteArray,
    private val today: () -> LocalDate = LocalDate::now,
    private val random: SecureRandom = SecureRandom(),
) {
    /** Returns the card data, or `null` if this is not a readable payment card. */
    fun read(): EmvCardData? {
        for (aid in candidateAids()) {
            val fci = command(selectCommand(aid)) ?: continue
            readApplication(fci)?.let { return it }
        }
        return null
    }

    private fun candidateAids(): List<ByteArray> {
        val ppse = command(selectCommand(PPSE))
        val fromDirectory = ppse?.let { Tlv.parse(it) }
            ?.flatMap { root -> collect(root, TAG_AID) }
            ?.map { it.value }
            .orEmpty()
        return fromDirectory + KNOWN_AIDS.map(String::hexToBytes).filter { known ->
            fromDirectory.none { it.contentEquals(known) }
        }
    }

    private fun readApplication(fci: ByteArray): EmvCardData? {
        val pdol = Tlv.parse(fci).find(TAG_PDOL)?.value
        val gpoTlv = command(gpoCommand(pdol))?.let { Tlv.parse(it) } ?: return null
        // Some cards (e.g. Visa) already return the data in the GPO response; others list records to read.
        val records = extract(gpoTlv)?.let { emptyList() } ?: aflRecords(gpoTlv).flatMap { (sfi, record) ->
            command(readRecordCommand(sfi, record))?.let { Tlv.parse(it) }.orEmpty()
        }
        return extract(records + gpoTlv)
    }

    /** (SFI, record number) pairs from the Application File Locator of a GPO response. */
    private fun aflRecords(gpo: List<Tlv>): List<Pair<Int, Int>> {
        val afl = gpo.find(TAG_AFL)?.value
            ?: gpo.find(TAG_RESPONSE_FORMAT_1)?.value?.drop(AIP_LENGTH)?.toByteArray()
            ?: return emptyList()
        return afl.toList().chunked(AFL_ENTRY_LENGTH).filter { it.size == AFL_ENTRY_LENGTH }.flatMap { entry ->
            val (sfiByte, first, last) = entry
            val sfi = sfiByte.unsigned() shr SFI_SHIFT
            (first.unsigned()..last.unsigned()).map { record -> sfi to record }
        }.take(MAX_RECORDS) // A real card needs a handful; a malformed AFL must not keep the reader busy.
    }

    private fun extract(tlv: List<Tlv>): EmvCardData? {
        val track2 = tlv.find(TAG_TRACK2)?.value?.toHex()
        val number = tlv.find(TAG_PAN)?.value?.toHex()?.trimEnd('F')
            ?: track2?.substringBefore('D')
            ?: return null
        if (number.length !in PAN_LENGTH || !number.all(Char::isDigit)) return null
        val expiry = tlv.find(TAG_EXPIRY)?.value?.toHex()?.let(::parseYymmdd)
            ?: track2?.substringAfter('D', "")?.take(YYMM_LENGTH)?.let(::parseYymm)
        val holder = (tlv.find(TAG_HOLDER_EXTENDED) ?: tlv.find(TAG_HOLDER))
            ?.value?.decodeToString()?.let(::normalizeHolder)
        return EmvCardData(number, expiry, holder)
    }

    /** Sends [apdu]; [retries] bounds how often the card may ask for a resend or for more data. */
    private fun command(apdu: ByteArray, retries: Int = MAX_RETRIES): ByteArray? {
        val response = try {
            transceive(apdu)
        } catch (_: IOException) {
            return null
        }
        if (response.size < STATUS_LENGTH) return null
        val sw1 = response[response.size - STATUS_LENGTH].unsigned()
        val sw2 = response.last().unsigned()
        return when {
            sw1 == SW1_OK && sw2 == 0 -> response.copyOf(response.size - STATUS_LENGTH)

            // "More data available": fetch it with GET RESPONSE.
            retries == 0 -> null

            sw1 == SW1_MORE_DATA -> command(getResponseCommand(sw2), retries - 1)

            // "Wrong length": repeat with the length the card asked for.
            sw1 == SW1_WRONG_LENGTH -> command(apdu.copyOf(apdu.size - 1) + sw2.toByte(), retries - 1)

            else -> null
        }
    }

    private fun gpoCommand(pdol: ByteArray?): ByteArray {
        val data = pdol?.let(::parseDol).orEmpty().flatMap { (tag, length) -> pdolValue(tag, length).toList() }
        val template = byteArrayOf(TAG_COMMAND_TEMPLATE.toByte(), data.size.toByte()) + data.toByteArray()
        return GET_PROCESSING_OPTIONS.hexToBytes() + template.size.toByte() + template + LE_ANY
    }

    /** Values a terminal would send; only what is needed to get the card to answer, no real transaction. */
    private fun pdolValue(tag: Int, length: Int): ByteArray {
        val value = when (tag) {
            TAG_TTQ -> "F620C000".hexToBytes()
            TAG_TERMINAL_COUNTRY, TAG_CURRENCY -> "0756".hexToBytes()
            TAG_TRANSACTION_DATE -> today().format(DateTimeFormatter.ofPattern("yyMMdd")).hexToBytes()
            TAG_UNPREDICTABLE_NUMBER -> ByteArray(length).also(random::nextBytes)
            else -> ByteArray(length)
        }
        return value.copyOf(length)
    }

    private fun normalizeHolder(raw: String): String? {
        // EMV stores "SURNAME/GIVEN NAMES"; cards hiding the name return "/" or blanks.
        val parts = raw.trim().split('/').map { it.trim() }.filter { it.isNotEmpty() }
        return when (parts.size) {
            0 -> null
            1 -> parts[0]
            else -> "${parts.drop(1).joinToString(" ")} ${parts[0]}"
        }?.takeIf { it.any(Char::isLetter) }
    }

    private fun parseYymmdd(value: String): YearMonth? = parseYymm(value.take(YYMM_LENGTH))

    private fun parseYymm(value: String): YearMonth? = runCatching {
        YearMonth.of(CENTURY + value.take(YEAR_DIGITS).toInt(), value.drop(YEAR_DIGITS).take(MONTH_DIGITS).toInt())
    }.getOrNull()

    private fun collect(tlv: Tlv, tag: Int): List<Tlv> =
        (if (tlv.tag == tag) listOf(tlv) else emptyList()) + tlv.children.flatMap { collect(it, tag) }

    private fun getResponseCommand(length: Int) = GET_RESPONSE.hexToBytes() + length.toByte()

    private fun selectCommand(aid: ByteArray) = SELECT_BY_NAME.hexToBytes() + aid.size.toByte() + aid + LE_ANY

    private fun readRecordCommand(sfi: Int, record: Int) =
        READ_RECORD.hexToBytes() + record.toByte() + ((sfi shl SFI_SHIFT) or READ_BY_SFI).toByte() + LE_ANY

    private companion object {
        // Command headers (CLA INS P1 P2) from ISO 7816-4 and EMV Book 3.
        const val SELECT_BY_NAME = "00A40400"
        const val GET_RESPONSE = "00C00000"
        const val READ_RECORD = "00B2"
        const val GET_PROCESSING_OPTIONS = "80A80000"
        const val LE_ANY: Byte = 0x00

        val PPSE = "2PAY.SYS.DDF01".encodeToByteArray()

        /** Fallback when a card has no PPSE directory. */
        val KNOWN_AIDS = listOf(
            "A0000000031010", // Visa credit/debit
            "A0000000032010", // Visa Electron
            "A0000000032020", // V Pay
            "A0000000041010", // Mastercard
            "A0000000043060", // Maestro
            "A00000002501", // American Express
            "A0000001523010", // Discover
            "A0000000651010", // JCB
            "A000000333010101", // UnionPay debit
            "A000000333010102", // UnionPay credit
        )

        const val TAG_AID = 0x4F
        const val TAG_PDOL = 0x9F38
        const val TAG_AFL = 0x94
        const val TAG_RESPONSE_FORMAT_1 = 0x80
        const val TAG_COMMAND_TEMPLATE = 0x83
        const val TAG_PAN = 0x5A
        const val TAG_TRACK2 = 0x57
        const val TAG_EXPIRY = 0x5F24
        const val TAG_HOLDER = 0x5F20
        const val TAG_HOLDER_EXTENDED = 0x9F0B
        const val TAG_TTQ = 0x9F66
        const val TAG_TERMINAL_COUNTRY = 0x9F1A
        const val TAG_CURRENCY = 0x5F2A
        const val TAG_TRANSACTION_DATE = 0x9A
        const val TAG_UNPREDICTABLE_NUMBER = 0x9F37

        const val AIP_LENGTH = 2
        const val AFL_ENTRY_LENGTH = 4
        const val MAX_RECORDS = 32
        const val SFI_SHIFT = 3
        const val READ_BY_SFI = 0x04
        const val STATUS_LENGTH = 2
        const val SW1_OK = 0x90
        const val SW1_MORE_DATA = 0x61
        const val SW1_WRONG_LENGTH = 0x6C
        const val MAX_RETRIES = 2
        const val YYMM_LENGTH = 4
        const val YEAR_DIGITS = 2
        const val MONTH_DIGITS = 2
        const val CENTURY = 2000
        val PAN_LENGTH = 12..19
    }
}
