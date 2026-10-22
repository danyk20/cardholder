package io.github.danyk20.cardholder.core.map

import io.github.danyk20.cardholder.core.domain.location.GeoMath
import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class WebMercatorTest {
    @Test
    fun `the world spans zero to one with the antimeridian at the edges`() {
        assertEquals(0.0, WebMercator.x(-180.0), 1e-12)
        assertEquals(1.0, WebMercator.x(180.0), 1e-12)
        assertEquals(0.5, WebMercator.x(0.0), 1e-12)
    }

    @Test
    fun `the top edge is the north and the bottom edge the south`() {
        assertEquals(0.5, WebMercator.y(0.0), 1e-12)
        assertTrue(WebMercator.y(48.0) < 0.5, "north is above the equator")
        assertTrue(WebMercator.y(-33.0) > 0.5, "south is below the equator")
    }

    @Test
    fun `latitudes beyond the projection are clamped to it`() {
        assertEquals(WebMercator.y(WebMercator.MAX_LATITUDE), WebMercator.y(89.9), 1e-12)
        assertEquals(WebMercator.y(-WebMercator.MAX_LATITUDE), WebMercator.y(-89.9), 1e-12)
        assertEquals(0.0, WebMercator.y(WebMercator.MAX_LATITUDE), 1e-4)
    }

    @Test
    fun `projecting and unprojecting gives the same place`() {
        listOf(
            GeoPoint(48.1486, 17.1077),
            GeoPoint(-33.8688, 151.2093),
            GeoPoint(0.0, 0.0),
            GeoPoint(GeoMath.MAX_LATITUDE, 179.9),
            GeoPoint(-GeoMath.MAX_LATITUDE, -179.9),
        ).forEach { point ->
            val (x, y) = WebMercator.toNormalized(point)
            val back = assertNotNull(WebMercator.toGeo(x, y))
            assertEquals(point.latitude, back.latitude, 1e-9)
            assertEquals(point.longitude, back.longitude, 1e-9)
        }
    }

    @Test
    fun `a tap at the date line gives a valid longitude`() {
        val east = assertNotNull(WebMercator.toGeo(1.0, 0.5))
        val west = assertNotNull(WebMercator.toGeo(0.0, 0.5))

        assertEquals(-180.0, east.longitude, 1e-9)
        assertEquals(-180.0, west.longitude, 1e-9)
        assertTrue(GeoMath.normalised(east) != null)
    }

    @Test
    fun `a tap at the top of the map is kept within a valid latitude`() {
        val top = assertNotNull(WebMercator.toGeo(0.5, 0.0))

        assertEquals(GeoMath.MAX_LATITUDE, top.latitude, 1e-9)
        assertEquals(0.0, top.longitude, 1e-9)
    }

    @Test
    fun `a position that isn't a number is no place`() {
        assertNull(WebMercator.toGeo(Double.NaN, 0.5))
        assertNull(WebMercator.toGeo(0.5, Double.NaN))
    }

    @Test
    fun `a scale of one shows the last zoom level and every level out halves it`() {
        assertEquals(1.0, WebMercator.scaleOf(WebMercator.MAX_ZOOM.toDouble()), 1e-12)
        assertEquals(0.25, WebMercator.scaleOf(WebMercator.MAX_ZOOM - 2.0), 1e-12)
    }

    @Test
    fun `the map is 256 pixels per tile at the last zoom level`() {
        assertEquals(256 * (1 shl WebMercator.MAX_ZOOM), WebMercator.FULL_SIZE)
        assertEquals(WebMercator.MAX_ZOOM + 1, WebMercator.LEVEL_COUNT)
    }
}
