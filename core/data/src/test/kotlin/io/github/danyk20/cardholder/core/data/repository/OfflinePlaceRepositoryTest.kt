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

    private fun fix(point: GeoPoint) = LocationFix(point, 10f, now)

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
