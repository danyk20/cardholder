package io.github.danyk20.cardholder.core.map

import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Test

class MapCameraTest {
    private val prague = GeoPoint(50.0755, 14.4378)
    private val bratislava = GeoPoint(48.1486, 17.1077)
    private val vienna = GeoPoint(48.2082, 16.3738)

    @Test
    fun `without places the whole world is shown`() {
        assertEquals(FitPlan.World, MapFit.plan(emptyList()))
    }

    @Test
    fun `one place is a spot to zoom in on`() {
        assertEquals(FitPlan.Spot(bratislava), MapFit.plan(listOf(bratislava)))
    }

    @Test
    fun `places at the same spot are a spot too`() {
        assertEquals(FitPlan.Spot(bratislava), MapFit.plan(listOf(bratislava, bratislava.copy())))
    }

    @Test
    fun `several places are fitted by their bounding box`() {
        val plan = MapFit.plan(listOf(bratislava, vienna, prague))

        val area = assertIs<FitPlan.Area>(plan).bounds
        // Prague is the westernmost place and Bratislava the easternmost one.
        assertEquals(WebMercator.x(prague.longitude), area.left, 1e-12)
        assertEquals(WebMercator.x(bratislava.longitude), area.right, 1e-12)
        assertEquals(WebMercator.y(prague.latitude), area.top, 1e-12)
        assertEquals(WebMercator.y(bratislava.latitude), area.bottom, 1e-12)
        assertTrue(area.left < area.right && area.top < area.bottom)
    }

    @Test
    fun `the bounding box contains every place`() {
        val points = listOf(bratislava, vienna, prague)
        val area = assertIs<FitPlan.Area>(MapFit.plan(points)).bounds

        points.forEach { point ->
            val (x, y) = WebMercator.toNormalized(point)
            assertTrue(x in area.left..area.right && y in area.top..area.bottom)
        }
    }

    @Test
    fun `the camera's focus keeps its point and zoom`() {
        val focus = MapCamera.Focus(bratislava, zoom = 15.0)

        assertEquals(bratislava, focus.point)
        assertEquals(15.0, focus.zoom)
    }

    @Test
    fun `focusing defaults to the zoom that shows one place`() {
        assertEquals(MapFit.SINGLE_PLACE_ZOOM, MapCamera.Focus(bratislava).zoom)
    }

    @Test
    fun `focuses are told apart by identity, so focusing again moves the map`() {
        val first = MapCamera.Focus(bratislava)
        val second = MapCamera.Focus(bratislava)

        assertTrue(first !== second)
    }
}
