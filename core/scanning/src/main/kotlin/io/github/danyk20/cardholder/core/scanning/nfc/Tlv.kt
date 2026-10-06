package io.github.danyk20.cardholder.core.scanning.nfc

/** A BER-TLV data object as used by EMV cards. */
internal class Tlv(val tag: Int, val value: ByteArray, val children: List<Tlv>) {
    /** Depth-first search for the first object with [tag]. */
    fun find(tag: Int): Tlv? = if (this.tag == tag) this else children.firstNotNullOfOrNull { it.find(tag) }

    companion object {
        /** EMV templates nest a few levels at most; deeper data is malformed and not descended into. */
        private const val MAX_DEPTH = 8

        /** Parses all objects in [data]; malformed trailing data is ignored. */
        fun parse(data: ByteArray): List<Tlv> = parse(data, depth = 0)

        private fun parse(data: ByteArray, depth: Int): List<Tlv> {
            val cursor = ByteCursor(data)
            return generateSequence { cursor.readObject(depth) }.toList()
        }

        private fun ByteCursor.readObject(depth: Int): Tlv? {
            // Padding between objects is allowed by EMV.
            while (hasMore() && skipPadding()) Unit
            val tag = readTag() ?: return null
            val value = readLength()?.let(::read) ?: return null
            val children = if (tag.isConstructed && depth < MAX_DEPTH) parse(value, depth + 1) else emptyList()
            return Tlv(tag.value, value, children)
        }
    }
}

internal fun List<Tlv>.find(tag: Int): Tlv? = firstNotNullOfOrNull { it.find(tag) }

/** Parses a Data Object List (tag + length pairs without values), e.g. a PDOL. */
internal fun parseDol(data: ByteArray): List<Pair<Int, Int>> {
    val cursor = ByteCursor(data)
    return generateSequence {
        val tag = cursor.readTag()
        val length = cursor.readByte()
        if (tag != null && length != null) tag.value to length else null
    }.toList()
}

internal class TlvTag(val value: Int, val isConstructed: Boolean)

/** Sequential reader over BER-TLV encoded bytes; every read returns `null` when the data ends early. */
internal class ByteCursor(private val data: ByteArray) {
    private var index = 0

    fun hasMore() = index < data.size

    fun readByte(): Int? = if (index < data.size) data[index++].unsigned() else null

    fun read(count: Int): ByteArray? =
        if (index + count <= data.size) data.copyOfRange(index, index + count).also { index += count } else null

    fun skipPadding(): Boolean {
        val next = data[index].unsigned()
        if (next != PADDING && next != PADDING_FF) return false
        index++
        return true
    }

    /** Tags whose low five bits are all set continue in following bytes while bit 8 is set. */
    fun readTag(): TlvTag? {
        val first = readByte() ?: return null
        var tag = first
        if (first and MORE_TAG_BYTES == MORE_TAG_BYTES) {
            do {
                val next = readByte() ?: return null
                tag = (tag shl Byte.SIZE_BITS) or next
            } while (next and NEXT_TAG_BYTE != 0)
        }
        return TlvTag(tag, isConstructed = first and CONSTRUCTED_BIT != 0)
    }

    /** Short form (< 128) or long form (0x81..0x83 followed by up to three length bytes). */
    fun readLength(): Int? {
        val first = readByte() ?: return null
        if (first and LONG_LENGTH == 0) return first
        return (first and LENGTH_BYTES_MASK)
            .takeIf { it in 1..MAX_LENGTH_BYTES }
            ?.let(::read)
            ?.fold(0) { length, byte -> (length shl Byte.SIZE_BITS) or byte.unsigned() }
    }

    private companion object {
        const val CONSTRUCTED_BIT = 0x20
        const val MORE_TAG_BYTES = 0x1F
        const val NEXT_TAG_BYTE = 0x80
        const val LONG_LENGTH = 0x80
        const val LENGTH_BYTES_MASK = 0x7F
        const val MAX_LENGTH_BYTES = 3
        const val PADDING = 0x00
        const val PADDING_FF = 0xFF
    }
}

private const val BYTE_MASK = 0xFF

internal fun Byte.unsigned(): Int = toInt() and BYTE_MASK

internal fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }

internal fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(HEX_RADIX).toByte() }.toByteArray()

private const val HEX_RADIX = 16
