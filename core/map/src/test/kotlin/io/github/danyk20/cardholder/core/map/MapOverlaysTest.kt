package io.github.danyk20.cardholder.core.map

import io.github.danyk20.cardholder.core.domain.location.GeoMath
import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

class MapOverlaysTest {
    private val shop = GeoPoint(48.1486, 17.1077)

    @Test
    fun `a place's marker is numbered and keeps its id`() {
        val overlay = MapOverlays.of(listOf(marker(id = 7, number = 3)), editPin = null).single()

        assertEquals("place:7", overlay.id)
        assertEquals("3", overlay.label)
        assertEquals(WebMercator.x(shop.longitude), overlay.x, 1e-12)
        assertEquals(WebMercator.y(shop.latitude), overlay.y, 1e-12)
    }

    @Test
    fun `a learned place is styled as learned`() {
        assertEquals(MarkerStyle.LEARNED, MapOverlays.styleOf(marker()))
    }

    @Test
    fun `a pinned place is styled as pinned`() {
        assertEquals(MarkerStyle.PINNED, MapOverlays.styleOf(marker(isPinned = true)))
    }

    @Test
    fun `the selected place stands out even when it is pinned`() {
        assertEquals(MarkerStyle.SELECTED, MapOverlays.styleOf(marker(isPinned = true, isSelected = true)))
    }

    @Test
    fun `the old position of a place being moved is faded, even when it is selected`() {
        assertEquals(MarkerStyle.FADED, MapOverlays.styleOf(marker(isSelected = true, isFaded = true)))
    }

    @Test
    fun `a place's accuracy is drawn as a circle around it`() {
        val overlay = MapOverlays.of(listOf(marker(accuracy = 25f)), editPin = null).single()

        assertEquals(48, assertNotNull(overlay.circle).size)
    }

    @Test
    fun `a place without accuracy has no circle`() {
        assertNull(MapOverlays.of(listOf(marker(accuracy = null)), editPin = null).single().circle)
    }

    @Test
    fun `the pending pin is drawn last, as an edit marker without label or circle`() {
        val places = listOf(marker(id = 1), marker(id = 2))
        val withPin = MapOverlays.of(places, editPin = GeoPoint(48.2, 17.1))

        assertEquals(MapOverlays.of(places, editPin = null), withPin.dropLast(1))
        val pin = withPin.last()
        assertEquals(MapOverlays.EDIT_ID, pin.id)
        assertEquals(MarkerStyle.EDIT, pin.style)
        assertNull(pin.label)
        assertNull(pin.circle)
    }

    @Test
    fun `an overlay id maps back to its place, and the pending pin to none`() {
        assertEquals(42L, MapOverlays.placeIdOf(MapOverlays.idOf(42L)))
        assertNull(MapOverlays.placeIdOf(MapOverlays.EDIT_ID))
        assertNull(MapOverlays.placeIdOf("place:not-a-number"))
        assertNull(MapOverlays.placeIdOf("accuracy:place:1"))
    }

    @Test
    fun `the accuracy circle is as wide as the accuracy on the ground`() {
        listOf(0.0, 48.1486, -33.8688, 70.0).forEach { latitude ->
            val centre = GeoPoint(latitude, 17.1077)
            val circle = assertNotNull(MapOverlays.circleOf(centre, radiusMeters = 150.0))

            circle.forEach { (x, y) ->
                val point = assertNotNull(WebMercator.toGeo(x, y))
                assertEquals(150.0, GeoMath.distanceMeters(centre, point), 0.5, "at latitude $latitude")
            }
        }
    }

    @Test
    fun `the accuracy circle wraps around the date line`() {
        val centre = GeoPoint(0.0, 179.9999)
        val circle = assertNotNull(MapOverlays.circleOf(centre, radiusMeters = 500.0))

        circle.forEach { (x, y) ->
            val point = assertNotNull(WebMercator.toGeo(x, y))
            assertEquals(500.0, GeoMath.distanceMeters(centre, point), 1.0)
        }
    }

    @Test
    fun `a circle with no radius is no circle`() {
        assertNull(MapOverlays.circleOf(shop, radiusMeters = 0.0))
        assertNull(MapOverlays.circleOf(shop, radiusMeters = -5.0))
        assertNull(MapOverlays.circleOf(shop, radiusMeters = Double.NaN))
    }

    private fun marker(
        id: Long = 1,
        number: Int = 1,
        accuracy: Float? = 10f,
        isSelected: Boolean = false,
        isPinned: Boolean = false,
        isFaded: Boolean = false,
    ) = MapMarker(
        id = id,
        point = shop,
        accuracyMeters = accuracy,
        number = number,
        isSelected = isSelected,
        isPinned = isPinned,
        isFaded = isFaded,
    )
}
