package io.github.danyk20.cardholder.core.domain.usecase

import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.domain.location.LocationPermission
import io.github.danyk20.cardholder.core.domain.location.Place
import io.github.danyk20.cardholder.core.domain.location.PlaceGeofence
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.PlaceSuggestions
import io.github.danyk20.cardholder.core.testing.data.TestCards
import io.github.danyk20.cardholder.core.testing.repository.FakeCardRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeGeofenceRegistrar
import io.github.danyk20.cardholder.core.testing.repository.FakeLocationAccess
import io.github.danyk20.cardholder.core.testing.repository.FakePlaceRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeUserPreferencesRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ArrivalUseCasesTest {
    private val now = Instant.parse("2026-10-18T12:00:00Z")
    private val cards = FakeCardRepository()
    private val places = FakePlaceRepository(cards).also { it.now = now }
    private val preferences = FakeUserPreferencesRepository()
    private val access = FakeLocationAccess(permission = LocationPermission.PRECISE_ALWAYS)
    private val registrar = FakeGeofenceRegistrar()
    private val sync = SyncGeofencesUseCase(places, preferences, access, registrar)
    private val arrival = ArrivalSuggestionUseCase(
        cards,
        places,
        preferences,
        access,
        sync,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    @Before
    fun setUp() = runTest {
        cards.add(TestCards.loyalty.copy(suggestByLocation = true), TestCards.loyaltyDetails)
        cards.add(TestCards.visa, TestCards.visaDetails)
        places.add(place(1, TestCards.loyalty.id, accuracy = 12f))
        places.add(place(2, TestCards.visa.id))
        places.add(place(3, TestCards.loyalty.id, centre = null))
    }

    @Test
    fun `registers geofences only for arrival notifications`() = runTest {
        sync.sync()
        assertNull(registrar.registered)

        preferences.setPlaceSuggestions(PlaceSuggestions.ARRIVAL)
        sync.sync()

        assertEquals(listOf(PlaceGeofence(1, GeoPoint(48.1486, 17.1077), 150f)), registrar.registered)
    }

    @Test
    fun `registers again only after a change`() = runTest {
        preferences.setPlaceSuggestions(PlaceSuggestions.ARRIVAL)
        sync.sync()
        sync.sync()
        assertEquals(1, registrar.replaceCalls)

        places.add(place(4, TestCards.loyalty.id, centre = GeoPoint(48.2, 17.2), accuracy = 100f))
        sync.sync()

        assertEquals(2, registrar.replaceCalls)
        assertEquals(200f, registrar.registered?.last()?.radiusMeters)
    }

    @Test
    fun `removes geofences without location all the time`() = runTest {
        preferences.setPlaceSuggestions(PlaceSuggestions.ARRIVAL)
        sync.sync()

        access.permission = LocationPermission.PRECISE
        sync.sync()

        assertNull(registrar.registered)
    }

    @Test
    fun `notifies about the card of the place and remembers it`() = runTest {
        preferences.setPlaceSuggestions(PlaceSuggestions.ARRIVAL)

        val suggestion = arrival.onArrival(listOf(1))

        assertEquals(TestCards.loyalty.id, suggestion?.card?.id)
        assertEquals(now, places.lastNotifiedAt(TestCards.loyalty.id))
        // Soon after, nothing more.
        assertNull(arrival.onArrival(listOf(1)))
    }

    @Test
    fun `nothing without notifications or after turning arrival notifications off`() = runTest {
        preferences.setPlaceSuggestions(PlaceSuggestions.ARRIVAL)
        sync.sync()
        access.notificationsAllowed = false
        assertNull(arrival.onArrival(listOf(1)))

        access.notificationsAllowed = true
        preferences.setPlaceSuggestions(PlaceSuggestions.IN_APP)
        assertNull(arrival.onArrival(listOf(1)))
        assertNull(registrar.registered)
    }

    @Test
    fun `not this place forgets it`() = runTest {
        arrival.notThisPlace(1)

        assertEquals(listOf(2L, 3L), places.places.value.map { it.id })
        assertEquals(setOf(TestCards.visa.id), arrival.onDeparture(listOf(2, 99)))
    }

    private fun place(
        id: Long,
        cardId: CardId,
        centre: GeoPoint? = GeoPoint(48.14861, 17.10771),
        accuracy: Float = 10f,
    ) = Place(id, cardId, centre, accuracy, useCount = 1, lastUsedAt = now.minusSeconds(86_400))
}
