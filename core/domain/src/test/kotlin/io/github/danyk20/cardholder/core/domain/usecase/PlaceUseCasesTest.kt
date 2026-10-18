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

    private fun place(cardId: CardId, centre: GeoPoint, accessPoints: List<AccessPoint> = emptyList()) =
        Place(cardId.hashCode().toLong(), cardId, centre, 10f, 1, now, accessPoints = accessPoints)

    private companion object {
        val Shop = GeoPoint(48.1486, 17.1077)
    }
}
