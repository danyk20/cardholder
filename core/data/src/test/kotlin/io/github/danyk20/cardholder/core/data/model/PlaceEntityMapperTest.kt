package io.github.danyk20.cardholder.core.data.model

import io.github.danyk20.cardholder.core.database.model.PlaceAccessPointEntity
import io.github.danyk20.cardholder.core.database.model.PlaceEntity
import io.github.danyk20.cardholder.core.database.model.PlaceFixEntity
import io.github.danyk20.cardholder.core.database.model.PlaceWithAccessPoints
import io.github.danyk20.cardholder.core.domain.location.Bssid
import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class PlaceEntityMapperTest {
    @Test
    fun `a learned place is not pinned`() {
        assertFalse(entity().toPlace().isPinned)
    }

    @Test
    fun `a place the user set is pinned`() {
        assertTrue(entity(pinned = true).toPlace().isPinned)
    }

    @Test
    fun `keeps when the place was created`() {
        val place = PlaceWithAccessPoints(entity(createdAt = 1_000L, lastUsedAt = 9_000L), emptyList(), emptyList())
            .toPlace()

        assertEquals(Instant.ofEpochMilli(1_000L), place.createdAt)
        assertEquals(Instant.ofEpochMilli(9_000L), place.lastUsedAt)
    }

    @Test
    fun `counts the fixes the place was loaded with`() {
        val place = PlaceWithAccessPoints(entity(), emptyList(), fixes = listOf(fix(1), fix(2), fix(3))).toPlace()

        assertEquals(3, place.fixCount)
    }

    @Test
    fun `removed access points are kept apart from the ones that count`() {
        val place = PlaceWithAccessPoints(
            entity(),
            listOf(accessPoint(1, blocked = false), accessPoint(2, blocked = true)),
            emptyList(),
        ).toPlace()

        assertEquals(listOf(Bssid(1)), place.accessPoints.map { it.bssid })
        assertEquals(listOf(Bssid(2)), place.blockedAccessPoints.map { it.bssid })
    }

    @Test
    fun `a place without fixes has none`() {
        assertEquals(0, PlaceWithAccessPoints(entity(), emptyList(), emptyList()).toPlace().fixCount)
    }

    @Test
    fun `a plain entity has no fix count until one is given`() {
        assertEquals(0, entity().toPlace().fixCount)
        assertEquals(4, entity().toPlace(fixCount = 4).fixCount)
    }

    @Test
    fun `a place without coordinates has no centre`() {
        assertNull(entity(latitude = null, longitude = null).toPlace().centre)
        assertNull(entity(latitude = 48.0, longitude = null).toPlace().centre)
    }

    @Test
    fun `a place with coordinates keeps its centre`() {
        assertEquals(GeoPoint(48.1486, 17.1077), entity().toPlace().centre)
    }

    private fun entity(
        latitude: Double? = 48.1486,
        longitude: Double? = 17.1077,
        createdAt: Long = 1_000L,
        lastUsedAt: Long = 2_000L,
        pinned: Boolean = false,
    ) = PlaceEntity(
        id = 7,
        cardId = "card",
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = 10f,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
        useCount = 2,
        pinned = pinned,
    )

    private fun fix(seed: Int) = PlaceFixEntity(
        placeId = 7,
        latitude = seed.toDouble(),
        longitude = 17.0,
        accuracyMeters = 10f,
        recordedAt = seed.toLong(),
    )

    private fun accessPoint(bssid: Long, blocked: Boolean) = PlaceAccessPointEntity(
        placeId = 1,
        bssid = bssid,
        ssid = "net-$bssid",
        rssi = -60,
        seenCount = 1,
        lastSeenAt = 0,
        blocked = blocked,
    )
}
