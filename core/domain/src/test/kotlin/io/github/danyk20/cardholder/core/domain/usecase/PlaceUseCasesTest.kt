package io.github.danyk20.cardholder.core.domain.usecase

import io.github.danyk20.cardholder.core.domain.location.AccessPoint
import io.github.danyk20.cardholder.core.domain.location.Bssid
import io.github.danyk20.cardholder.core.domain.location.FixPrecision
import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.domain.location.LocationFix
import io.github.danyk20.cardholder.core.domain.location.LocationPermission
import io.github.danyk20.cardholder.core.domain.location.NearbyCard
import io.github.danyk20.cardholder.core.domain.location.NearbyReason
import io.github.danyk20.cardholder.core.domain.location.Place
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.BrandRef
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.model.PlaceSuggestions
import io.github.danyk20.cardholder.core.testing.data.TestCards
import io.github.danyk20.cardholder.core.testing.repository.FakeCardRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeLocationAccess
import io.github.danyk20.cardholder.core.testing.repository.FakePlaceRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeShopRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeSurroundingsProvider
import io.github.danyk20.cardholder.core.testing.repository.FakeUserPreferencesRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class PlaceUseCasesTest {
    private val cards = FakeCardRepository()
    private val places = FakePlaceRepository(cards)
    private val preferences = FakeUserPreferencesRepository()
    private val sensors = FakeSurroundingsProvider()
    private val access = FakeLocationAccess()
    private var now = Instant.parse("2026-10-18T12:00:00Z")
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?) = this

        override fun instant() = now
    }
    private val scope = TestScope()
    private val remember = RememberPlaceUseCase(cards, places, preferences, sensors, access, scope)
    private val nearby =
        FindNearbyCardsUseCase(cards, places, FakeShopRepository(), preferences, sensors, access, clock)
    private val cardPlaces = CardPlaces(cards, places, preferences, remember, nearby)

    private val kaufland = TestCards.loyalty.copy(
        id = CardId("kaufland"),
        info = CardInfo.Loyalty(BrandRef.Custom("Kaufland Card"), BarcodeFormat.EAN_13),
        suggestByLocation = true,
    )

    @Before
    fun setUp() = runTest {
        cards.add(kaufland, TestCards.loyaltyDetails)
        cards.add(TestCards.visa, TestCards.visaDetails)
        preferences.setPlaceSuggestions(PlaceSuggestions.IN_APP)
        sensors.fix = LocationFix(Shop, 12f, now)
        sensors.accessPoints = listOf(AccessPoint(Bssid(1), "Shop", -60))
    }

    @Test
    fun `remembers where a card is used with a precise fix`() = runTest {
        assertEquals(RememberResult.REMEMBERED, remember(kaufland.id))

        assertEquals(listOf(kaufland.id), places.visits.map { it.first })
        assertEquals(FixPrecision.HIGH, sensors.fixRequests.single().precision)
    }

    @Test
    fun `remembers nothing while switched off or not allowed`() = runTest {
        assertEquals(RememberResult.SWITCHED_OFF, remember(TestCards.visa.id))

        access.locationEnabled = false
        assertEquals(RememberResult.LOCATION_OFF, remember(kaufland.id))

        access.permission = LocationPermission.APPROXIMATE
        assertEquals(RememberResult.NO_PERMISSION, remember(kaufland.id))

        preferences.setPlaceSuggestions(PlaceSuggestions.OFF)
        assertEquals(RememberResult.SWITCHED_OFF, remember(kaufland.id))

        assertEquals(0, sensors.wifiReads + sensors.fixReads)
        assertEquals(emptyList(), places.visits)
    }

    @Test
    fun `reports when nothing was found`() = runTest {
        sensors.fix = null
        sensors.accessPoints = emptyList()

        assertEquals(RememberResult.NOTHING_FOUND, remember(kaufland.id))
    }

    @Test
    fun `finds cards used nearby`() = runTest {
        places.add(place(kaufland.id, Shop.copy(latitude = Shop.latitude + 0.003)))
        sensors.accessPoints = emptyList()

        val found = nearby(CardType.entries.toSet())

        assertEquals(listOf(kaufland.id), found.map { it.cardId })
        assertEquals(FixPrecision.BALANCED, sensors.fixRequests.single().precision)
    }

    @Test
    fun `wifi of the place makes the location unnecessary`() = runTest {
        places.add(place(kaufland.id, Shop, listOf(AccessPoint(Bssid(1), "Shop", -60))))

        assertEquals(listOf(NearbyCard(kaufland.id, NearbyReason.WifiHere(1.0))), nearby(setOf(CardType.LOYALTY)))
        assertEquals(0, sensors.fixReads)
    }

    @Test
    fun `the shop's network suggests its card before any place is known`() = runTest {
        sensors.accessPoints = listOf(AccessPoint(Bssid(9), "Kaufland-Free", -65))

        assertEquals(listOf(kaufland.id), nearby(setOf(CardType.LOYALTY)).map { it.cardId })
        // Without any known place, the location can't help.
        assertEquals(0, sensors.fixReads)
    }

    @Test
    fun `reads no sensors when nothing can be suggested`() = runTest {
        places.add(place(kaufland.id, Shop))

        assertEquals(emptyList(), nearby(setOf(CardType.BANK)))
        preferences.setPlaceSuggestions(PlaceSuggestions.OFF)
        assertEquals(emptyList(), nearby(CardType.entries.toSet()))
        preferences.setPlaceSuggestions(PlaceSuggestions.IN_APP)
        access.permission = LocationPermission.NONE
        assertEquals(emptyList(), nearby(CardType.entries.toSet()))

        assertEquals(0, sensors.wifiReads + sensors.fixReads)
    }

    @Test
    fun `reuses the result for a minute`() = runTest {
        places.add(place(kaufland.id, Shop))

        nearby(CardType.entries.toSet())
        now = now.plusSeconds(59)
        nearby(setOf(CardType.LOYALTY))
        assertEquals(1, sensors.wifiReads)

        now = now.plusSeconds(2)
        nearby(setOf(CardType.LOYALTY))
        assertEquals(2, sensors.wifiReads)

        nearby.invalidate()
        nearby(setOf(CardType.LOYALTY))
        assertEquals(3, sensors.wifiReads)
    }

    @Test
    fun `moving a place pins it at the point and forgets the old nearby result`() = runTest {
        places.add(place(kaufland.id, Shop, listOf(AccessPoint(Bssid(1), "Shop", -60))))
        nearby(setOf(CardType.LOYALTY))
        assertEquals(1, sensors.wifiReads)
        val target = Shop.copy(latitude = Shop.latitude + 0.0007)

        assertTrue(cardPlaces.move(placeId = placeId(), point = target))

        val moved = places.places.value.single()
        assertSamePoint(target, moved.centre)
        assertTrue(moved.isPinned)
        assertEquals(listOf(Bssid(1)), moved.accessPoints.map { it.bssid })
        nearby(setOf(CardType.LOYALTY))
        assertEquals(2, sensors.wifiReads)
    }

    @Test
    fun `moving a place normalises the longitude of the point`() = runTest {
        places.add(place(kaufland.id, Shop))

        cardPlaces.move(placeId = placeId(), point = GeoPoint(Shop.latitude, Shop.longitude + 360.0))

        assertSamePoint(Shop, places.places.value.single().centre)
    }

    @Test
    fun `moving a place to a point that is not on the map changes nothing`() = runTest {
        places.add(place(kaufland.id, Shop))
        nearby(setOf(CardType.LOYALTY))

        listOf(
            GeoPoint(Double.NaN, 17.0),
            GeoPoint(48.0, Double.NaN),
            GeoPoint(86.0, 17.0),
            GeoPoint(-86.0, 17.0),
        ).forEach { point -> assertFalse(cardPlaces.move(placeId = placeId(), point = point)) }

        assertSamePoint(Shop, places.places.value.single().centre)
        assertFalse(places.places.value.single().isPinned)
        nearby(setOf(CardType.LOYALTY))
        assertEquals(1, sensors.wifiReads)
    }

    @Test
    fun `moving a place that no longer exists is reported and changes nothing`() = runTest {
        places.add(place(kaufland.id, Shop))

        assertFalse(cardPlaces.move(placeId = 99L, point = Shop.copy(latitude = Shop.latitude + 0.001)))

        assertSamePoint(Shop, places.places.value.single().centre)
    }

    @Test
    fun `adding a place pins it on the card at the point`() = runTest {
        places.add(place(kaufland.id, Shop))
        nearby(setOf(CardType.LOYALTY))
        val target = Shop.copy(latitude = Shop.latitude + 0.002)

        val id = assertNotNull(cardPlaces.add(kaufland.id, target))

        val added = places.places.value.single { it.id == id }
        assertEquals(kaufland.id, added.cardId)
        assertSamePoint(target, added.centre)
        assertTrue(added.isPinned)
        nearby(setOf(CardType.LOYALTY))
        assertEquals(2, sensors.wifiReads)
    }

    @Test
    fun `adding a place rejects a point that is not on the map`() = runTest {
        assertNull(cardPlaces.add(kaufland.id, GeoPoint(Double.NaN, Double.NaN)))
        assertNull(cardPlaces.add(kaufland.id, GeoPoint(-85.5, 17.0)))

        assertEquals(emptyList(), places.places.value)
    }

    @Test
    fun `adding a place normalises the longitude`() = runTest {
        val id = assertNotNull(cardPlaces.add(kaufland.id, GeoPoint(Shop.latitude, 181.0)))

        assertEquals(-179.0, places.places.value.single { it.id == id }.centre?.longitude ?: error("no centre"), 1e-9)
    }

    @Test
    fun `deleting a place forgets only that place and the nearby result`() = runTest {
        places.add(place(kaufland.id, Shop))
        places.add(place(TestCards.visa.id, Shop.north(5_000.0)))
        nearby(setOf(CardType.LOYALTY))

        cardPlaces.delete(placeId = places.places.value.first { it.cardId == kaufland.id }.id)

        assertEquals(listOf(TestCards.visa.id), places.places.value.map { it.cardId })
        nearby(setOf(CardType.LOYALTY))
        assertEquals(2, sensors.wifiReads)
    }

    @Test
    fun `observes the places of a card, the most used first`() = runTest {
        places.add(place(kaufland.id, Shop).copy(id = 1, useCount = 1))
        places.add(place(kaufland.id, Shop.north(500.0)).copy(id = 2, useCount = 4))
        places.add(place(TestCards.visa.id, Shop).copy(id = 3, useCount = 9))

        assertEquals(listOf(2L, 1L), cardPlaces.observePlaces(kaufland.id).first().map { it.id })
    }

    private fun place(cardId: CardId, centre: GeoPoint, accessPoints: List<AccessPoint> = emptyList()) =
        Place(cardId.hashCode().toLong(), cardId, centre, 10f, 1, now, accessPoints = accessPoints)

    private fun placeId() = places.places.value.single().id

    private fun GeoPoint.north(meters: Double) = copy(latitude = latitude + meters / 111_195.0)

    /** Longitudes pass through wrapping, so they're compared with a tolerance of about a micrometre. */
    private fun assertSamePoint(expected: GeoPoint, actual: GeoPoint?) {
        assertNotNull(actual)
        assertEquals(expected.latitude, actual.latitude, 1e-9)
        assertEquals(expected.longitude, actual.longitude, 1e-9)
    }

    private companion object {
        val Shop = GeoPoint(48.1486, 17.1077)
    }
}
