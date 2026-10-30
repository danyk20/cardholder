package io.github.danyk20.cardholder.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.database.CardholderDatabase
import io.github.danyk20.cardholder.core.database.model.CardEntity
import io.github.danyk20.cardholder.core.domain.location.AccessPoint
import io.github.danyk20.cardholder.core.domain.location.Bssid
import io.github.danyk20.cardholder.core.domain.location.GeoMath
import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.domain.location.LocationFix
import io.github.danyk20.cardholder.core.domain.location.Surroundings
import io.github.danyk20.cardholder.core.model.CardId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflinePlaceRepositoryTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        CardholderDatabase::class.java,
    ).allowMainThreadQueries().build()
    private var now = Instant.parse("2026-10-18T10:00:00Z")
    private val repository by lazy {
        OfflinePlaceRepository(
            dao = database.placeDao(),
            clock = object : Clock() {
                override fun getZone() = ZoneOffset.UTC

                override fun withZone(zone: java.time.ZoneId?) = this

                override fun instant() = now
            },
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @Before
    fun setUp() = runTest {
        database.cardDao().upsert(card("kaufland"))
        database.cardDao().upsert(card("lidl"))
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `nothing is learned from nothing`() = runTest {
        assertFalse(repository.recordVisit(Kaufland, Surroundings(emptyList(), null)))
        assertEquals(0, repository.observeTotal().first())
    }

    @Test
    fun `visits to the same shop refine one place`() = runTest {
        repeat(12) { visit ->
            now = now.plusSeconds(86_400)
            repository.recordVisit(Kaufland, Surroundings(listOf(ap(1)), fix(Shop.north(visit * 2.0))))
        }

        val place = repository.suggestionPlaces().single()
        assertEquals(12, place.useCount)
        // Only the last ten fixes (4 m to 22 m north) are left; their centre is 13 m north.
        assertEquals(13.0, GeoMath.distanceMeters(Shop, assertNotNull(place.centre)), 0.5)
    }

    @Test
    fun `another branch becomes another place`() = runTest {
        repository.recordVisit(Kaufland, Surroundings(listOf(ap(1)), fix(Shop)))
        now = now.plusSeconds(86_400)

        repository.recordVisit(Kaufland, Surroundings(listOf(ap(2)), fix(Shop.north(3_000.0))))

        assertEquals(2, repository.observeCount(Kaufland).first())
    }

    @Test
    fun `an access point seen far away travels and is forgotten`() = runTest {
        repository.recordVisit(Kaufland, Surroundings(listOf(ap(1), ap(7)), fix(Shop)))
        now = now.plusSeconds(3_600)

        repository.recordVisit(Lidl, Surroundings(listOf(ap(7), ap(8)), fix(Shop.north(5_000.0))))

        val places = repository.suggestionPlaces().associateBy { it.cardId }
        assertEquals(listOf(Bssid(1)), places.getValue(Kaufland).accessPoints.map { it.bssid })
        assertEquals(listOf(Bssid(8)), places.getValue(Lidl).accessPoints.map { it.bssid })
    }

    @Test
    fun `remembers notifications per card`() = runTest {
        repository.recordVisit(Kaufland, Surroundings(emptyList(), fix(Shop)))
        val place = repository.suggestionPlaces().single()

        repository.markNotified(place.id)

        assertEquals(now, repository.lastNotifiedAt())
        assertEquals(now, repository.lastNotifiedAt(Kaufland))
        assertEquals(null, repository.lastNotifiedAt(Lidl))
    }

    @Test
    fun `forgets places of a card or all of them`() = runTest {
        repository.recordVisit(Kaufland, Surroundings(emptyList(), fix(Shop)))
        repository.recordVisit(Lidl, Surroundings(emptyList(), fix(Shop.north(3_000.0))))

        repository.forgetCard(Kaufland)
        assertEquals(listOf(Lidl), repository.suggestionPlaces().map { it.cardId })

        repository.forgetAll()
        assertTrue(repository.suggestionPlaces().isEmpty())
    }

    @Test
    fun `moving a place pins it at the point and keeps its Wi-Fi`() = runTest {
        repository.recordVisit(Kaufland, Surroundings(listOf(ap(1)), fix(Shop)))
        val place = repository.suggestionPlaces().single()
        val target = Shop.north(80.0)

        assertTrue(repository.movePlace(place.id, target))

        val moved = repository.suggestionPlaces().single()
        assertTrue(moved.isPinned)
        assertEquals(25f, moved.accuracyMeters)
        assertEquals(1, moved.fixCount)
        assertEquals(0.0, GeoMath.distanceMeters(target, assertNotNull(moved.centre)), 0.01)
        assertEquals(listOf(Bssid(1)), moved.accessPoints.map { it.bssid })
        assertEquals(place.useCount, moved.useCount)
    }

    @Test
    fun `later visits to a pinned place count and add Wi-Fi but never move it`() = runTest {
        repository.recordVisit(Kaufland, Surroundings(listOf(ap(1)), fix(Shop)))
        val id = repository.suggestionPlaces().single().id
        val pin = Shop.north(80.0)
        repository.movePlace(id, pin)

        // The Wi-Fi of the place is seen, the GPS is 60 m off: the same place, its position stays.
        now = now.plusSeconds(86_400)
        repository.recordVisit(Kaufland, Surroundings(listOf(ap(1), ap(2)), fix(Shop.north(60.0))))
        // No Wi-Fi in common, but 120 m from the pin: it joins the pinned place.
        now = now.plusSeconds(86_400)
        repository.recordVisit(Kaufland, Surroundings(listOf(ap(3)), fix(pin.north(120.0))))
        // No Wi-Fi in common and 400 m from the pin: that's another place.
        now = now.plusSeconds(86_400)
        repository.recordVisit(Kaufland, Surroundings(listOf(ap(4)), fix(pin.north(400.0))))

        val places = repository.suggestionPlaces().associateBy { it.id }
        assertEquals(2, places.size)
        val pinned = assertNotNull(places[id])
        assertEquals(3, pinned.useCount)
        assertTrue(pinned.isPinned)
        assertEquals(0.0, GeoMath.distanceMeters(pin, assertNotNull(pinned.centre)), 0.01)
        assertEquals(setOf(Bssid(1), Bssid(2), Bssid(3)), pinned.accessPoints.map { it.bssid }.toSet())
        val other = assertNotNull(places.values.singleOrNull { it.id != id })
        assertEquals(1, other.useCount)
        assertFalse(other.isPinned)
        assertEquals(1, other.fixCount)
    }

    @Test
    fun `a place added on the map is pinned and counted for its card`() = runTest {
        val id = repository.addPlace(Kaufland, Shop)

        val added = repository.observePlaces(Kaufland).first().single()
        assertEquals(id, added.id)
        assertEquals(Kaufland, added.cardId)
        assertTrue(added.isPinned)
        assertEquals(1, added.useCount)
        assertEquals(now, added.createdAt)
        assertEquals(now, added.lastUsedAt)
        assertEquals(25f, added.accuracyMeters)
        assertEquals(1, added.fixCount)
        assertEquals(0.0, GeoMath.distanceMeters(Shop, assertNotNull(added.centre)), 0.01)
        assertEquals(0, repository.observeCount(Lidl).first())
    }

    @Test
    fun `a visit near an added place joins it without moving it`() = runTest {
        val id = repository.addPlace(Kaufland, Shop)
        now = now.plusSeconds(86_400)

        repository.recordVisit(Kaufland, Surroundings(emptyList(), fix(Shop.north(100.0))))

        val place = repository.observePlaces(Kaufland).first().single()
        assertEquals(id, place.id)
        assertEquals(2, place.useCount)
        assertEquals(0.0, GeoMath.distanceMeters(Shop, assertNotNull(place.centre)), 0.01)
    }

    @Test
    fun `observed places are the card's own, the most used first`() = runTest {
        repository.recordVisit(Kaufland, Surroundings(emptyList(), fix(Shop)))
        repository.recordVisit(Kaufland, Surroundings(emptyList(), fix(Shop.north(3_000.0))))
        now = now.plusSeconds(86_400)
        repository.recordVisit(Kaufland, Surroundings(emptyList(), fix(Shop.north(3_000.0))))
        repository.recordVisit(Lidl, Surroundings(emptyList(), fix(Shop)))

        val observed = repository.observePlaces(Kaufland).first()

        assertEquals(listOf(2, 1), observed.map { it.useCount })
        assertTrue(observed.all { it.cardId == Kaufland })
    }

    @Test
    fun `moving a place that was forgotten reports it and changes nothing`() = runTest {
        assertFalse(repository.movePlace(999L, Shop))
        assertEquals(0, repository.observeTotal().first())
    }

    private fun fix(point: GeoPoint) = LocationFix(point, 10f, now)

    @Test
    fun `a removed network stops matching and isn't learned again, until it's restored`() = runTest {
        repository.recordVisit(Kaufland, Surroundings(listOf(ap(1), ap(7)), fix(Shop)))
        val id = repository.suggestionPlaces().single().id

        repository.setAccessPointsBlocked(id, listOf(Bssid(7)), blocked = true)
        now = now.plusSeconds(86_400)
        repository.recordVisit(Kaufland, Surroundings(listOf(ap(1), ap(7)), fix(Shop)))

        val place = repository.suggestionPlaces().single()
        assertEquals(listOf(Bssid(1)), place.accessPoints.map { it.bssid })
        assertEquals(listOf(Bssid(7)), place.blockedAccessPoints.map { it.bssid })
        val stored = repository.observeAccessPoints(id).first().associateBy { it.accessPoint.bssid }
        assertTrue(stored.getValue(Bssid(7)).isBlocked)
        assertEquals(1, stored.getValue(Bssid(7)).seenCount)
        assertEquals(2, stored.getValue(Bssid(1)).seenCount)

        repository.setAccessPointsBlocked(id, listOf(Bssid(7)), blocked = false)

        assertEquals(
            setOf(Bssid(1), Bssid(7)),
            repository.suggestionPlaces().single().accessPoints.map {
                it.bssid
            }.toSet(),
        )
    }

    private fun ap(bssid: Long) = AccessPoint(Bssid(bssid), "net-$bssid", -60)

    private fun GeoPoint.north(meters: Double) = copy(latitude = latitude + meters / 111_195.0)

    private fun card(id: String) = CardEntity(
        id = id,
        type = "LOYALTY",
        title = id,
        color = "BLUE",
        isLocked = false,
        createdAt = 1,
        updatedAt = 2,
        bankNetwork = null,
        idCountry = null,
        loyaltyShopId = null,
        loyaltyShopName = id,
        loyaltyBarcodeFormat = "QR_CODE",
        frontImage = null,
        backImage = null,
        sealedDetails = byteArrayOf(1),
        sealedCvv = null,
        suggestByLocation = true,
    )

    private companion object {
        val Kaufland = CardId("kaufland")
        val Lidl = CardId("lidl")
        val Shop = GeoPoint(48.1486, 17.1077)
    }
}
