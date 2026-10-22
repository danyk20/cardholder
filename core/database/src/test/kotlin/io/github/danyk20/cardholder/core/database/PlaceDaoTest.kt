package io.github.danyk20.cardholder.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.database.dao.CardDao
import io.github.danyk20.cardholder.core.database.dao.PlaceDao
import io.github.danyk20.cardholder.core.database.model.CardEntity
import io.github.danyk20.cardholder.core.database.model.PlaceCentre
import io.github.danyk20.cardholder.core.database.model.PlaceFixEntity
import io.github.danyk20.cardholder.core.database.model.VisitAccessPoint
import io.github.danyk20.cardholder.core.database.model.VisitFix
import io.github.danyk20.cardholder.core.database.model.VisitWrite
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaceDaoTest {
    private lateinit var database: CardholderDatabase
    private lateinit var cards: CardDao
    private lateinit var dao: PlaceDao

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CardholderDatabase::class.java,
        ).allowMainThreadQueries().build()
        cards = database.cardDao()
        dao = database.placeDao()
        cards.upsert(card("card", suggest = true))
        cards.upsert(card("other", suggest = false))
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `a new place gets the fix and its centre`() = runTest {
        val id = dao.applyVisit(visit(fix = fix(1, accuracy = 8f)), ::latest)

        val place = dao.places(listOf(id)).single().place
        assertEquals(1, place.useCount)
        assertEquals(1.0, place.latitude)
        assertEquals(8f, place.accuracyMeters)
    }

    @Test
    fun `a place keeps its ten latest fixes`() = runTest {
        val id = dao.applyVisit(visit(fix = fix(0)), ::latest)
        (1..10).forEach { dao.applyVisit(visit(placeId = id, fix = fix(it), now = it.toLong()), ::latest) }

        val fixes = dao.fixes(id)
        assertEquals((10 downTo 1).map(Int::toDouble), fixes.map { it.latitude })
        assertEquals(11, dao.places(listOf(id)).single().place.useCount)
        assertEquals(10.0, dao.places(listOf(id)).single().place.latitude)
    }

    @Test
    fun `the same visit keeps only the most accurate fix`() = runTest {
        val id = dao.applyVisit(visit(fix = fix(1, accuracy = 30f)), ::latest)

        dao.applyVisit(visit(placeId = id, sameVisit = true, fix = fix(2, accuracy = 50f)), ::latest)
        dao.applyVisit(visit(placeId = id, sameVisit = true, fix = fix(3, accuracy = 10f)), ::latest)

        assertEquals(listOf(3.0), dao.fixes(id).map { it.latitude })
        assertEquals(1, dao.places(listOf(id)).single().place.useCount)
    }

    @Test
    fun `a place keeps the access points seen most`() = runTest {
        val id = dao.applyVisit(visit(accessPoints = (1L..20L).map(::accessPoint)), ::latest)
        dao.applyVisit(
            visit(placeId = id, accessPoints = (2L..20L).map(::accessPoint) + accessPoint(21), now = 1),
            ::latest,
        )

        val stored = dao.accessPoints(id).associateBy { it.bssid }
        assertEquals((2L..21L).toSet(), stored.keys)
        assertEquals(2, stored.getValue(2).seenCount)
        assertEquals(1, stored.getValue(21).seenCount)
    }

    @Test
    fun `travelling access points are forgotten everywhere`() = runTest {
        val id = dao.applyVisit(visit(accessPoints = listOf(accessPoint(1), accessPoint(2))), ::latest)

        dao.applyVisit(visit(cardId = "other", travelling = listOf(1)), ::latest)

        assertEquals(listOf(2L), dao.accessPoints(id).map { it.bssid })
        assertEquals(listOf(1L), dao.travelling())
        assertEquals(listOf(id), dao.placesWithAccessPoints(listOf(2)).map { it.place.id })
    }

    @Test
    fun `a card can have any number of places`() = runTest {
        repeat(25) { dao.applyVisit(visit(fix = fix(it)), ::latest) }

        assertEquals(25, dao.observeCount("card").first())
    }

    @Test
    fun `deleting a card deletes its places`() = runTest {
        val id = dao.applyVisit(visit(fix = fix(1), accessPoints = listOf(accessPoint(1))), ::latest)

        cards.delete("card")

        assertEquals(0, dao.observeTotal().first())
        assertEquals(emptyList(), dao.fixes(id))
        assertEquals(emptyList(), dao.accessPoints(id))
    }

    @Test
    fun `saving a card again keeps its places`() = runTest {
        dao.applyVisit(visit(fix = fix(1)), ::latest)

        cards.upsert(card("card", suggest = true).copy(title = "Renamed"))

        assertEquals(1, dao.observeCount("card").first())
    }

    @Test
    fun `suggestions only use cards that have them switched on`() = runTest {
        val suggested = dao.applyVisit(visit(fix = fix(1)), ::latest)
        dao.applyVisit(visit(cardId = "other", fix = fix(2)), ::latest)
        dao.applyVisit(visit(accessPoints = listOf(accessPoint(5))), ::latest)

        assertEquals(2, dao.suggestionPlaces().size)
        assertEquals(listOf(suggested), dao.observeGeofencePlaces(limit = 10).first().map { it.id })
    }

    @Test
    fun `remembers the last notification`() = runTest {
        val id = dao.applyVisit(visit(fix = fix(1)), ::latest)

        dao.markNotified(id, 42)

        assertEquals(42L, dao.lastNotifiedAt())
        assertEquals(42L, dao.lastNotifiedAt("card"))
        assertEquals(null, dao.lastNotifiedAt("other"))
    }

    @Test
    fun `forgetting all clears places and travelling access points`() = runTest {
        dao.applyVisit(visit(fix = fix(1), travelling = listOf(9)), ::latest)

        dao.forgetAll()

        assertEquals(0, dao.observeTotal().first())
        assertTrue(dao.travelling().isEmpty())
    }

    @Test
    fun `moving a place leaves one fix at the pin and keeps its access points`() = runTest {
        val id = dao.applyVisit(visit(fix = fix(1), accessPoints = listOf(accessPoint(1), accessPoint(2))), ::latest)
        dao.applyVisit(visit(placeId = id, fix = fix(2), accessPoints = listOf(accessPoint(3)), now = 1), ::latest)
        assertEquals(2, dao.fixes(id).size)

        val moved = dao.movePlace(id, latitude = 5.0, longitude = 6.0, accuracy = 25f, at = 99)

        assertTrue(moved)
        val fixes = dao.fixes(id)
        assertEquals(1, fixes.size)
        assertEquals(5.0, fixes.single().latitude)
        assertEquals(6.0, fixes.single().longitude)
        assertEquals(99L, fixes.single().recordedAt)
        val place = dao.places(listOf(id)).single().place
        assertEquals(5.0, place.latitude)
        assertEquals(6.0, place.longitude)
        assertEquals(25f, place.accuracyMeters)
        assertTrue(place.pinned)
        assertEquals(setOf(1L, 2L, 3L), dao.accessPoints(id).map { it.bssid }.toSet())
    }

    @Test
    fun `moving a place that does not exist changes nothing`() = runTest {
        assertFalse(dao.movePlace(404L, latitude = 5.0, longitude = 6.0, accuracy = 25f, at = 99))

        assertEquals(0, dao.observeTotal().first())
        assertEquals(emptyList(), dao.fixes(404L))
    }

    @Test
    fun `a visit to a pinned place counts and merges access points but keeps its position`() = runTest {
        val id = dao.applyVisit(visit(fix = fix(1), accessPoints = listOf(accessPoint(1))), ::latest)
        dao.movePlace(id, latitude = 5.0, longitude = 6.0, accuracy = 25f, at = 1)

        val returned = dao.applyVisit(
            visit(
                placeId = id,
                fix = fix(9),
                accessPoints = listOf(accessPoint(1), accessPoint(2)),
                now = 100,
            ),
            ::latest,
        )

        assertEquals(id, returned)
        val place = dao.places(listOf(id)).single().place
        assertEquals(2, place.useCount)
        assertEquals(100L, place.lastUsedAt)
        assertEquals(5.0, place.latitude)
        assertEquals(6.0, place.longitude)
        assertEquals(25f, place.accuracyMeters)
        assertTrue(place.pinned)
        assertEquals(listOf(5.0), dao.fixes(id).map { it.latitude })
        val stored = dao.accessPoints(id).associateBy { it.bssid }
        assertEquals(setOf(1L, 2L), stored.keys)
        assertEquals(2, stored.getValue(1).seenCount)
        assertEquals(1, stored.getValue(2).seenCount)
    }

    @Test
    fun `a pinned place keeps its position on a visit that continues the last one`() = runTest {
        val id = dao.applyVisit(visit(fix = fix(1)), ::latest)
        dao.movePlace(id, latitude = 5.0, longitude = 6.0, accuracy = 25f, at = 1)

        dao.applyVisit(visit(placeId = id, sameVisit = true, fix = fix(8), now = 2), ::latest)

        val place = dao.places(listOf(id)).single().place
        assertEquals(1, place.useCount)
        assertEquals(5.0, place.latitude)
        assertEquals(listOf(5.0), dao.fixes(id).map { it.latitude })
    }

    @Test
    fun `a pinned place is not moved by a visit even when its centre function would move it`() = runTest {
        val id = dao.applyVisit(visit(fix = fix(1)), ::latest)
        dao.movePlace(id, latitude = 5.0, longitude = 6.0, accuracy = 25f, at = 1)

        dao.applyVisit(visit(placeId = id, fix = fix(7), now = 2)) { PlaceCentre(99.0, 99.0, 1f) }

        val place = dao.places(listOf(id)).single().place
        assertEquals(5.0, place.latitude)
        assertEquals(6.0, place.longitude)
    }

    @Test
    fun `a place added on the map is pinned, used once and has its fix`() = runTest {
        val id = dao.addPinnedPlace("card", latitude = 5.0, longitude = 6.0, accuracy = 25f, at = 7)

        val stored = dao.places(listOf(id)).single()
        assertTrue(stored.place.pinned)
        assertEquals(1, stored.place.useCount)
        assertEquals(7L, stored.place.createdAt)
        assertEquals(7L, stored.place.lastUsedAt)
        assertEquals(5.0, stored.place.latitude)
        assertEquals(25f, stored.place.accuracyMeters)
        assertEquals(listOf(5.0), stored.fixes.map { it.latitude })
        assertEquals(1, dao.observeCount("card").first())
    }

    @Test
    fun `a visit near an added place counts as a visit and does not move it`() = runTest {
        val id = dao.addPinnedPlace("card", latitude = 5.0, longitude = 6.0, accuracy = 25f, at = 7)

        dao.applyVisit(visit(placeId = id, fix = fix(9), now = 100), ::latest)

        val place = dao.places(listOf(id)).single().place
        assertEquals(2, place.useCount)
        assertEquals(5.0, place.latitude)
    }

    @Test
    fun `places of a card are observed most used first, then most recently used`() = runTest {
        val first = dao.applyVisit(visit(fix = fix(1), now = 0), ::latest)
        val second = dao.applyVisit(visit(fix = fix(2), now = 10), ::latest)
        dao.applyVisit(visit(placeId = first, now = 20), ::latest)
        dao.applyVisit(visit(placeId = second, now = 30), ::latest)
        val third = dao.applyVisit(visit(fix = fix(3), now = 5), ::latest)
        dao.applyVisit(visit(cardId = "other", fix = fix(4), now = 50), ::latest)
        dao.applyVisit(visit(cardId = "other", fix = fix(4), now = 60), ::latest)
        dao.applyVisit(visit(cardId = "other", fix = fix(4), now = 70), ::latest)

        val observed = dao.observePlacesOf("card").first()

        assertEquals(listOf(second, first, third), observed.map { it.place.id })
        assertEquals(listOf(2, 2, 1), observed.map { it.place.useCount })
    }

    @Test
    fun `observed places carry their fixes and access points`() = runTest {
        val id = dao.applyVisit(
            visit(fix = fix(1), accessPoints = listOf(accessPoint(1), accessPoint(2))),
            ::latest,
        )
        dao.applyVisit(visit(placeId = id, fix = fix(2), now = 1), ::latest)

        val observed = dao.observePlacesOf("card").first().single()

        assertEquals(2, observed.fixes.size)
        assertEquals(setOf(1L, 2L), observed.accessPoints.map { it.bssid }.toSet())
    }

    /** The centre of the latest fix, enough to see that the centre follows the stored fixes. */
    private fun latest(fixes: List<PlaceFixEntity>) =
        fixes.firstOrNull()?.let { PlaceCentre(it.latitude, it.longitude, it.accuracyMeters) }

    private fun visit(
        cardId: String = "card",
        placeId: Long? = null,
        sameVisit: Boolean = false,
        fix: VisitFix? = null,
        accessPoints: List<VisitAccessPoint> = emptyList(),
        travelling: List<Long> = emptyList(),
        now: Long = 0,
    ) = VisitWrite(
        cardId = cardId,
        placeId = placeId,
        sameVisit = sameVisit,
        fix = fix,
        accessPoints = accessPoints,
        travelling = travelling,
        now = now,
        keepFixes = 10,
        keepAccessPoints = 20,
    )

    private fun fix(latitude: Int, accuracy: Float = 10f) =
        VisitFix(latitude.toDouble(), longitude = 17.0, accuracyMeters = accuracy, recordedAt = latitude.toLong())

    private fun accessPoint(bssid: Long) = VisitAccessPoint(bssid, "net-$bssid", -60)

    private fun card(id: String, suggest: Boolean) = CardEntity(
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
        loyaltyShopName = "Shop",
        loyaltyBarcodeFormat = "QR_CODE",
        frontImage = null,
        backImage = null,
        sealedDetails = byteArrayOf(1, 2, 3),
        sealedCvv = null,
        suggestByLocation = suggest,
    )
}
