package io.github.danyk20.cardholder.core.domain.location

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

class GeoMathTest {
    @Test
    fun `measures distances on the globe`() {
        assertEquals(111.2, GeoMath.distanceMeters(GeoPoint(0.0, 0.0), GeoPoint(0.001, 0.0)), 0.1)
        assertEquals(111_195.0, GeoMath.distanceMeters(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0)), 1.0)
        assertEquals(20_015_114.0, GeoMath.distanceMeters(GeoPoint(0.0, 0.0), GeoPoint(0.0, 180.0)), 1.0)
        assertEquals(0.0, GeoMath.distanceMeters(Shop, Shop), 0.0)
    }

    @Test
    fun `has no centre without fixes`() {
        assertNull(GeoMath.centre(emptyList()))
    }

    @Test
    fun `weights precise fixes more`() {
        val centre = assertNotNull(GeoMath.centre(listOf(fix(Shop, 10f), fix(Shop.north(30.0), 30f))))

        // Weights 1/10² and 1/30² put the centre a tenth of the way: 3 m north.
        assertEquals(3.0, GeoMath.distanceMeters(Shop, centre.point), 0.1)
        assertEquals(9.5f, centre.accuracyMeters, 0.1f)
    }

    @Test
    fun `no fix counts as more accurate than 5 m`() {
        val centre = assertNotNull(GeoMath.centre(listOf(fix(Shop, 1f), fix(Shop.north(10.0), 5f))))

        assertEquals(5.0, GeoMath.distanceMeters(Shop, centre.point), 0.1)
    }

    @Test
    fun `drops outliers once there are three fixes`() {
        val fixes = listOf(fix(Shop), fix(Shop.north(10.0)), fix(Shop.north(5.0)), fix(Shop.north(900.0)))

        val centre = assertNotNull(GeoMath.centre(fixes))

        assertEquals(5.0, GeoMath.distanceMeters(Shop, centre.point), 0.1)
    }

    @Test
    fun `averages across the antimeridian`() {
        val centre = assertNotNull(GeoMath.centre(listOf(fix(GeoPoint(0.0, 179.9999)), fix(GeoPoint(0.0, -179.9999)))))

        assertEquals(180.0, kotlin.math.abs(centre.point.longitude), 0.0001)
    }
}
