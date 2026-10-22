package io.github.danyk20.cardholder.core.location

import io.github.danyk20.cardholder.core.model.CountryCode

/**
 * A country's outline, as bundled in `assets/country_borders.bin` (see `tools/country-borders/README.md`).
 * Rings are stored without their closing point and are treated as closed.
 */
internal class CountryBorder(
    val code: CountryCode,
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
    val polygons: List<BorderPolygon>,
)

/** A polygon: its first ring is the outer edge, the others are holes. */
internal class BorderPolygon(val rings: List<BorderRing>)

internal class BorderRing(val lat: DoubleArray, val lon: DoubleArray) {
    val size: Int get() = lat.size
}

/** Decodes the bundled border file. The layout is documented in `tools/country-borders/README.md`. */
internal object CountryBorderFormat {
    const val VERSION = 1
    private const val SCALE = 10_000.0
    private val MAGIC = "CHCB".toByteArray(Charsets.US_ASCII)

    fun decode(bytes: ByteArray): List<CountryBorder> {
        val reader = ByteReader(bytes)
        require(reader.bytes(MAGIC.size).contentEquals(MAGIC)) { "Not a country borders file" }
        val version = reader.byte()
        require(version == VERSION) { "Unsupported country borders version $version" }
        val countries = List(reader.varint().toInt()) { readCountry(reader) }
        require(reader.isAtEnd) { "Unexpected data after the last country" }
        return countries
    }

    private fun readCountry(reader: ByteReader): CountryBorder {
        val code = requireNotNull(CountryCode.of(String(reader.bytes(2), Charsets.US_ASCII))) { "Invalid country code" }
        val minLat = reader.zigzag() / SCALE
        val minLon = reader.zigzag() / SCALE
        val maxLat = reader.zigzag() / SCALE
        val maxLon = reader.zigzag() / SCALE
        val polygons =
            List(reader.varint().toInt()) { BorderPolygon(List(reader.varint().toInt()) { readRing(reader) }) }
        return CountryBorder(code, minLat, minLon, maxLat, maxLon, polygons)
    }

    private fun readRing(reader: ByteReader): BorderRing {
        val count = reader.varint().toInt()
        require(count >= MIN_RING_POINTS) { "A ring needs at least $MIN_RING_POINTS points" }
        val lat = DoubleArray(count)
        val lon = DoubleArray(count)
        var previousLat = 0L
        var previousLon = 0L
        for (i in 0 until count) {
            previousLat += reader.zigzag()
            previousLon += reader.zigzag()
            lat[i] = previousLat / SCALE
            lon[i] = previousLon / SCALE
        }
        return BorderRing(lat, lon)
    }

    private const val MIN_RING_POINTS = 3
}

/** Reads unsigned LEB128 varints, zigzag-encoded signed varints and raw bytes. */
internal class ByteReader(private val bytes: ByteArray) {
    private var position = 0

    val isAtEnd: Boolean get() = position == bytes.size

    fun byte(): Int {
        require(position < bytes.size) { "Unexpected end of data" }
        return bytes[position++].toInt() and BYTE_MASK
    }

    fun bytes(count: Int): ByteArray {
        require(count >= 0 && position + count <= bytes.size) { "Unexpected end of data" }
        return bytes.copyOfRange(position, position + count).also { position += count }
    }

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val value = byte()
            require(shift < MAX_VARINT_BITS) { "Varint too long" }
            result = result or ((value and PAYLOAD_MASK).toLong() shl shift)
            if (value and CONTINUATION == 0) return result
            shift += PAYLOAD_BITS
        }
    }

    fun zigzag(): Long {
        val encoded = varint()
        return (encoded ushr 1) xor -(encoded and 1)
    }

    private companion object {
        const val BYTE_MASK = 0xFF
        const val PAYLOAD_MASK = 0x7F
        const val CONTINUATION = 0x80
        const val PAYLOAD_BITS = 7
        const val MAX_VARINT_BITS = 64
    }
}
