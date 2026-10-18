package io.github.danyk20.cardholder.core.domain.location

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class PlaceClusteringTest {
    @Test
    fun `stores nothing without a usable fix or access point`() {
        assertNull(plan(Surroundings(emptyList(), null)))
        assertNull(plan(Surroundings(listOf(ap(1, rssi = -95)), fix(Shop, accuracy = 300f))))
    }

    @Test
    fun `drops inaccurate fixes but keeps the access points`() {
        val plan = assertNotNull(plan(Surroundings(listOf(ap(1)), fix(Shop, accuracy = 150f))))

        assertNull(plan.fix)
        assertEquals(listOf(ap(1)), plan.accessPoints)
    }

    @Test
    fun `joins a place of the card within 150 m`() {
        val places = listOf(place(1), place(2, centre = Shop.north(5_000.0)))

        assertEquals(1L, plan(Surroundings(emptyList(), fix(Shop.north(149.0)))) { places }?.placeId)
        assertNull(plan(Surroundings(emptyList(), fix(Shop.north(151.0)))) { places }?.placeId)
    }

    @Test
    fun `joins the nearest of several places`() {
        val places = listOf(place(1), place(2, centre = Shop.north(200.0)))

        assertEquals(2L, plan(Surroundings(emptyList(), fix(Shop.north(120.0)))) { places }?.placeId)
    }

    @Test
    fun `a shared access point joins a place even when the fix is off`() {
        val places = listOf(place(1, accessPoints = listOf(ap(7))), place(2, centre = Shop.north(600.0)))

        val plan = plan(Surroundings(listOf(ap(7)), fix(Shop.north(590.0)))) { places }

        assertEquals(1L, plan?.placeId)
    }

    @Test
    fun `prefers the place sharing the most access points`() {
        val places = listOf(
            place(1, accessPoints = listOf(ap(1))),
            place(2, centre = null, accessPoints = listOf(ap(1), ap(2))),
        )

        assertEquals(2L, plan(Surroundings(listOf(ap(1), ap(2)), null)) { places }?.placeId)
    }

    @Test
    fun `access points stored more than a kilometre away travel`() {
        val elsewhere = place(9, card = "other", centre = Shop.north(5_000.0), accessPoints = listOf(ap(7)))

        val plan = assertNotNull(
            PlaceClustering.plan(
                cardPlaces = emptyList(),
                knownPlaces = listOf(elsewhere),
                surroundings = Surroundings(listOf(ap(7), ap(8)), fix(Shop)),
                travelling = setOf(Bssid(8)),
                now = Now,
            ),
        )

        assertEquals(setOf(Bssid(7)), plan.travelling)
        assertEquals(emptyList(), plan.accessPoints)
        assertNull(plan.placeId)
    }

    @Test
    fun `uses within 15 minutes continue the visit`() {
        val recent = listOf(place(1, lastUsedAt = Now.minusSeconds(14 * 60)))
        val earlier = listOf(place(1, lastUsedAt = Now.minusSeconds(16 * 60)))

        assertTrue(plan(Surroundings(emptyList(), fix(Shop))) { recent }!!.sameVisit)
        assertFalse(plan(Surroundings(emptyList(), fix(Shop))) { earlier }!!.sameVisit)
    }

    private fun plan(surroundings: Surroundings, places: () -> List<Place> = { emptyList() }) =
        PlaceClustering.plan(places(), emptyList(), surroundings, emptySet(), Now)
}
