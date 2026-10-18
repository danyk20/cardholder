package io.github.danyk20.cardholder.core.domain.location

import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.testing.data.TestCards
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class NearbyMatcherTest {
    @Test
    fun `suggests cards used within a kilometre, the nearest first`() {
        val candidates = listOf(
            candidate("far", Shop.north(900.0)),
            candidate("near", Shop.north(200.0)),
            candidate("too-far", Shop.north(1_100.0)),
        )

        val nearby = NearbyMatcher.match(candidates, emptyList(), fix(Shop))

        assertEquals(listOf("near", "far"), nearby.map { it.cardId.value })
        assertEquals(200.0, (nearby.first().reason as NearbyReason.Distance).meters, 1.0)
    }

    @Test
    fun `ignores fixes too inaccurate to tell`() {
        val candidates = listOf(candidate("near", Shop))

        assertEquals(emptyList(), NearbyMatcher.match(candidates, emptyList(), fix(Shop, accuracy = 800f)))
    }

    @Test
    fun `access points of the place come before distance`() {
        val candidates = listOf(
            candidate("close", Shop),
            candidate("wifi", Shop.north(800.0), accessPoints = listOf(ap(1))),
        )

        val nearby = NearbyMatcher.match(candidates, listOf(ap(1, rssi = -80)), fix(Shop))

        assertEquals(listOf("wifi", "close"), nearby.map { it.cardId.value })
        assertEquals(NearbyReason.WifiHere(0.6), nearby.first().reason)
    }

    @Test
    fun `network names of the shop suggest its card before its first use`() {
        val kaufland = SuggestionCandidate(card("kaufland"), emptyList(), ShopKeywords.of("Kaufland Card"))

        assertEquals(
            listOf(NearbyCard(CardId("kaufland"), NearbyReason.WifiName(-70))),
            NearbyMatcher.match(listOf(kaufland), listOf(ap(1, "Kaufland-Free", rssi = -70)), null),
        )
        assertEquals(emptyList(), NearbyMatcher.match(listOf(kaufland), listOf(ap(1, "Kaufland-Free", -85)), null))
    }

    @Test
    fun `a network seen in several branches suggests the card in another one`() {
        val branches = listOf(
            place(1, accessPoints = listOf(ap(1, "LP-Guest"), ap(2, "Free WiFi"))),
            place(2, centre = Shop.north(20_000.0), accessPoints = listOf(ap(3, "LP-Guest"), ap(4, "Free WiFi"))),
        )
        val lidl = SuggestionCandidate(card("lidl"), branches, emptySet())

        assertEquals(
            listOf(NearbyCard(CardId("lidl"), NearbyReason.WifiChain(-60))),
            NearbyMatcher.match(listOf(lidl), listOf(ap(10, "LP-Guest")), null),
        )
        assertEquals(emptyList(), NearbyMatcher.match(listOf(lidl), listOf(ap(10, "Free WiFi")), null))
    }

    @Test
    fun `a network seen at a single place is no chain`() {
        val branch = place(1, accessPoints = listOf(ap(1, "LP-Guest")))
        val lidl = SuggestionCandidate(card("lidl"), listOf(branch), emptySet())

        assertEquals(emptyList(), NearbyMatcher.match(listOf(lidl), listOf(ap(10, "LP-Guest")), null))
    }

    @Test
    fun `suggests at most three cards`() {
        val candidates = (1..5).map { candidate("card-$it", Shop.north(it * 10.0)) }

        assertEquals(3, NearbyMatcher.match(candidates, emptyList(), fix(Shop)).size)
    }

    @Test
    fun `strong access points of a place make the location unnecessary`() {
        val candidates = listOf(candidate("wifi", Shop, accessPoints = listOf(ap(1), ap(2))))

        assertTrue(NearbyMatcher.isCertain(candidates, listOf(ap(1, rssi = -60))))
        assertFalse(NearbyMatcher.isCertain(candidates, listOf(ap(1, rssi = -75))))
        assertTrue(NearbyMatcher.isCertain(candidates, listOf(ap(1, rssi = -75), ap(2, rssi = -75))))
    }

    private fun candidate(id: String, centre: GeoPoint, accessPoints: List<AccessPoint> = emptyList()) =
        SuggestionCandidate(card(id), listOf(place(id.hashCode().toLong(), id, centre, accessPoints)), emptySet())

    private fun card(id: String) = TestCards.loyalty.copy(id = CardId(id), suggestByLocation = true)
}
