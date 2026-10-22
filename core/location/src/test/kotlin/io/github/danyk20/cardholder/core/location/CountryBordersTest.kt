package io.github.danyk20.cardholder.core.location

import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.model.CountryCode
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.Test

/** The border format and lookup, on a small outline with a hole, encoded here as the build script does. */
class CountryBordersTest {
    private val square = TestPolygon(
        outer = listOf(0.0 to 0.0, 0.0 to 10.0, 10.0 to 10.0, 10.0 to 0.0),
        holes = listOf(listOf(4.0 to 4.0, 4.0 to 6.0, 6.0 to 6.0, 6.0 to 4.0)),
    )
    private val country = TestCountry("AA", listOf(square))

    @Test
    fun `a point inside the outline belongs to the country`() {
        val lookup = CountryLookup(CountryBorderFormat.decode(encode(listOf(country))))

        assertEquals(code("AA"), lookup.countryAt(GeoPoint(latitude = 1.0, longitude = 1.0)))
    }

    @Test
    fun `a point inside a hole does not belong to the country`() {
        val lookup = CountryLookup(CountryBorderFormat.decode(encode(listOf(country))))

        assertNull(lookup.countryAt(GeoPoint(latitude = 5.0, longitude = 5.0)))
    }

    @Test
    fun `a point just off the coast takes the nearest country within 25 km`() {
        val lookup = CountryLookup(CountryBorderFormat.decode(encode(listOf(country))))

        // 0.2 degrees south of the edge is about 22 km.
        assertEquals(code("AA"), lookup.countryAt(GeoPoint(latitude = -0.2, longitude = 5.0)))
    }

    @Test
    fun `a point farther than 25 km from every country is no country`() {
        val lookup = CountryLookup(CountryBorderFormat.decode(encode(listOf(country))))

        // 0.3 degrees south of the edge is about 33 km.
        assertNull(lookup.countryAt(GeoPoint(latitude = -0.3, longitude = 5.0)))
    }

    @Test
    fun `the decoder reads the coordinates back in degrees`() {
        val decoded = CountryBorderFormat.decode(encode(listOf(country))).single()

        assertEquals(code("AA"), decoded.code)
        assertEquals(0.0, decoded.minLat, 1e-9)
        assertEquals(10.0, decoded.maxLon, 1e-9)
        assertEquals(2, decoded.polygons.single().rings.size)
    }

    @Test
    fun `a file with another magic is rejected`() {
        val bytes = encode(listOf(country))
        bytes[0] = 'X'.code.toByte()

        assertFailsWith<IllegalArgumentException> { CountryBorderFormat.decode(bytes) }
    }

    @Test
    fun `a file of another version is rejected`() {
        val bytes = encode(listOf(country))
        bytes[4] = 2

        assertFailsWith<IllegalArgumentException> { CountryBorderFormat.decode(bytes) }
    }

    @Test
    fun `a truncated file is rejected`() {
        val bytes = encode(listOf(country))

        assertFailsWith<IllegalArgumentException> { CountryBorderFormat.decode(bytes.copyOf(bytes.size - 3)) }
    }

    private fun code(value: String) = requireNotNull(CountryCode.of(value))
}

private class TestPolygon(val outer: List<Pair<Double, Double>>, val holes: List<List<Pair<Double, Double>>>) {
    val rings: List<List<Pair<Double, Double>>> get() = listOf(outer) + holes
}

private class TestCountry(val code: String, val polygons: List<TestPolygon>)

private const val SCALE = 10_000.0

/** Encodes countries in the layout of `tools/country-borders/README.md`, version 1. */
private fun encode(countries: List<TestCountry>): ByteArray {
    val out = ByteArrayOutputStream()
    out.write("CHCB".toByteArray(Charsets.US_ASCII))
    out.write(1)
    out.varint(countries.size.toLong())
    countries.forEach { out.writeCountry(it) }
    return out.toByteArray()
}

private fun ByteArrayOutputStream.writeCountry(country: TestCountry) {
    write(country.code.toByteArray(Charsets.US_ASCII))
    val points = country.polygons.flatMap { it.rings.flatten() }
    zigzag(Math.round(points.minOf { it.first } * SCALE))
    zigzag(Math.round(points.minOf { it.second } * SCALE))
    zigzag(Math.round(points.maxOf { it.first } * SCALE))
    zigzag(Math.round(points.maxOf { it.second } * SCALE))
    varint(country.polygons.size.toLong())
    country.polygons.forEach { polygon ->
        varint(polygon.rings.size.toLong())
        polygon.rings.forEach { writeRing(it) }
    }
}

private fun ByteArrayOutputStream.writeRing(ring: List<Pair<Double, Double>>) {
    varint(ring.size.toLong())
    var previousLat = 0L
    var previousLon = 0L
    for ((lat, lon) in ring) {
        val latUnits = Math.round(lat * SCALE)
        val lonUnits = Math.round(lon * SCALE)
        zigzag(latUnits - previousLat)
        zigzag(lonUnits - previousLon)
        previousLat = latUnits
        previousLon = lonUnits
    }
}

private fun ByteArrayOutputStream.varint(value: Long) {
    var rest = value
    while (rest >= 0x80) {
        write(((rest and 0x7F) or 0x80).toInt())
        rest = rest ushr 7
    }
    write(rest.toInt())
}

private fun ByteArrayOutputStream.zigzag(value: Long) = varint((value shl 1) xor (value shr 63))
