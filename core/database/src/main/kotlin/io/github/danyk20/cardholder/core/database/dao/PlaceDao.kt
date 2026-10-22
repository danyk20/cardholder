package io.github.danyk20.cardholder.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.github.danyk20.cardholder.core.database.model.PlaceAccessPointEntity
import io.github.danyk20.cardholder.core.database.model.PlaceCentre
import io.github.danyk20.cardholder.core.database.model.PlaceEntity
import io.github.danyk20.cardholder.core.database.model.PlaceFixEntity
import io.github.danyk20.cardholder.core.database.model.PlaceWithAccessPoints
import io.github.danyk20.cardholder.core.database.model.TravellingAccessPointEntity
import io.github.danyk20.cardholder.core.database.model.VisitWrite
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions") // One function per query the app needs.
interface PlaceDao {
    @Transaction
    @Query(
        """
        SELECT places.* FROM places JOIN cards ON cards.id = places.card_id
        WHERE cards.suggest_by_location = 1
        """,
    )
    suspend fun suggestionPlaces(): List<PlaceWithAccessPoints>

    @Query(
        """
        SELECT places.* FROM places JOIN cards ON cards.id = places.card_id
        WHERE cards.suggest_by_location = 1 AND places.latitude IS NOT NULL AND places.longitude IS NOT NULL
        ORDER BY places.use_count DESC, places.last_used_at DESC
        LIMIT :limit
        """,
    )
    fun observeGeofencePlaces(limit: Int): Flow<List<PlaceEntity>>

    @Transaction
    @Query("SELECT * FROM places WHERE card_id = :cardId")
    suspend fun placesOf(cardId: String): List<PlaceWithAccessPoints>

    /** The places of one card, the most used first, for the card's Places screen. */
    @Transaction
    @Query("SELECT * FROM places WHERE card_id = :cardId ORDER BY use_count DESC, last_used_at DESC")
    fun observePlacesOf(cardId: String): Flow<List<PlaceWithAccessPoints>>

    @Transaction
    @Query("SELECT * FROM places WHERE id IN (:ids)")
    suspend fun places(ids: Collection<Long>): List<PlaceWithAccessPoints>

    /** Places of any card where one of [bssids] was seen. */
    @Transaction
    @Query(
        """
        SELECT * FROM places WHERE id IN (SELECT place_id FROM place_access_points WHERE bssid IN (:bssids))
        """,
    )
    suspend fun placesWithAccessPoints(bssids: List<Long>): List<PlaceWithAccessPoints>

    @Query("SELECT COUNT(*) FROM places WHERE card_id = :cardId")
    fun observeCount(cardId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM places")
    fun observeTotal(): Flow<Int>

    @Query("SELECT bssid FROM travelling_access_points")
    suspend fun travelling(): List<Long>

    @Query("SELECT * FROM place_fixes WHERE place_id = :placeId ORDER BY recorded_at DESC, id DESC")
    suspend fun fixes(placeId: Long): List<PlaceFixEntity>

    @Query("SELECT * FROM place_access_points WHERE place_id = :placeId")
    suspend fun accessPoints(placeId: Long): List<PlaceAccessPointEntity>

    @Query("UPDATE places SET last_notified_at = :at WHERE id = :id")
    suspend fun markNotified(id: Long, at: Long)

    @Query("SELECT MAX(last_notified_at) FROM places")
    suspend fun lastNotifiedAt(): Long?

    @Query("SELECT MAX(last_notified_at) FROM places WHERE card_id = :cardId")
    suspend fun lastNotifiedAt(cardId: String): Long?

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM places WHERE card_id = :cardId")
    suspend fun deleteForCard(cardId: String)

    @Query("DELETE FROM places")
    suspend fun deleteAll()

    @Query("DELETE FROM travelling_access_points")
    suspend fun deleteTravelling()

    /** Forgets every place and which access points travel. */
    @Transaction
    suspend fun forgetAll() {
        deleteAll()
        deleteTravelling()
    }

    /**
     * Stores one use of a card: creates or updates the place, adds the fix keeping only the latest
     * [VisitWrite.keepFixes], merges the access points keeping the [VisitWrite.keepAccessPoints] seen
     * most, and updates the place's centre computed by [centreOf] from its remaining fixes.
     *
     * A pinned place (set by the user) only counts the visit and merges the access points: its fixes
     * and centre stay where the user put it.
     *
     * @return the place's id.
     */
    @Transaction
    suspend fun applyVisit(visit: VisitWrite, centreOf: (List<PlaceFixEntity>) -> PlaceCentre?): Long {
        if (visit.travelling.isNotEmpty()) {
            insertTravelling(visit.travelling.map { TravellingAccessPointEntity(it, visit.now) })
            deleteAccessPoints(visit.travelling)
        }
        val pinned = visit.placeId?.let { isPinned(it) } == true
        val placeId = visit.placeId?.also { markUsed(it, visit.now, visits = if (visit.sameVisit) 0 else 1) }
            ?: insertPlace(PlaceEntity(cardId = visit.cardId, createdAt = visit.now, lastUsedAt = visit.now))
        if (!pinned) {
            addFix(placeId, visit)
        }
        mergeAccessPoints(placeId, visit)
        if (!pinned) {
            val centre = centreOf(fixes(placeId))
            setCentre(placeId, centre?.latitude, centre?.longitude, centre?.accuracyMeters)
        }
        return placeId
    }

    /**
     * Moves a place to the user's pin: its learned fixes are replaced by one fix at the pin, and it's
     * marked pinned so later visits don't move it again. Its access points are kept, since they were
     * seen at the real place; only the GPS position was wrong.
     *
     * @return `false` if there is no such place.
     */
    @Transaction
    suspend fun movePlace(id: Long, latitude: Double, longitude: Double, accuracy: Float, at: Long): Boolean {
        if (!exists(id)) return false
        deleteFixes(id)
        insertFix(
            PlaceFixEntity(
                placeId = id,
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracy,
                recordedAt = at,
            ),
        )
        setCentre(id, latitude, longitude, accuracy)
        markPinned(id)
        return true
    }

    /** Adds a place the user placed on the map, with the one fix at the pin and counted as used once. */
    @Transaction
    suspend fun addPinnedPlace(cardId: String, latitude: Double, longitude: Double, accuracy: Float, at: Long): Long {
        val id = insertPlace(
            PlaceEntity(
                cardId = cardId,
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracy,
                createdAt = at,
                lastUsedAt = at,
                pinned = true,
            ),
        )
        insertFix(
            PlaceFixEntity(
                placeId = id,
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracy,
                recordedAt = at,
            ),
        )
        return id
    }

    @Query("SELECT EXISTS(SELECT 1 FROM places WHERE id = :id)")
    suspend fun exists(id: Long): Boolean

    @Query("SELECT pinned FROM places WHERE id = :id")
    suspend fun isPinned(id: Long): Boolean

    @Query("UPDATE places SET pinned = 1 WHERE id = :id")
    suspend fun markPinned(id: Long)

    @Query("DELETE FROM place_fixes WHERE place_id = :placeId")
    suspend fun deleteFixes(placeId: Long)

    @Insert
    suspend fun insertPlace(place: PlaceEntity): Long

    @Query("UPDATE places SET last_used_at = :now, use_count = use_count + :visits WHERE id = :id")
    suspend fun markUsed(id: Long, now: Long, visits: Int)

    @Query("UPDATE places SET latitude = :latitude, longitude = :longitude, accuracy_m = :accuracy WHERE id = :id")
    suspend fun setCentre(id: Long, latitude: Double?, longitude: Double?, accuracy: Float?)

    @Insert
    suspend fun insertFix(fix: PlaceFixEntity)

    @Query("DELETE FROM place_fixes WHERE id = :id")
    suspend fun deleteFix(id: Long)

    /** Deletes all but the [keep] latest fixes of a place. */
    @Query(
        """
        DELETE FROM place_fixes WHERE place_id = :placeId AND id NOT IN (
            SELECT id FROM place_fixes WHERE place_id = :placeId ORDER BY recorded_at DESC, id DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimFixes(placeId: Long, keep: Int)

    @Upsert
    suspend fun upsertAccessPoints(accessPoints: List<PlaceAccessPointEntity>)

    /** Deletes all but the [keep] access points of a place seen on most visits. */
    @Query(
        """
        DELETE FROM place_access_points WHERE place_id = :placeId AND bssid NOT IN (
            SELECT bssid FROM place_access_points WHERE place_id = :placeId
            ORDER BY seen_count DESC, last_seen_at DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimAccessPoints(placeId: Long, keep: Int)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTravelling(accessPoints: List<TravellingAccessPointEntity>)

    @Query("DELETE FROM place_access_points WHERE bssid IN (:bssids)")
    suspend fun deleteAccessPoints(bssids: List<Long>)
}

private suspend fun PlaceDao.addFix(placeId: Long, visit: VisitWrite) {
    val fix = visit.fix ?: return
    val entity = PlaceFixEntity(
        placeId = placeId,
        latitude = fix.latitude,
        longitude = fix.longitude,
        accuracyMeters = fix.accuracyMeters,
        recordedAt = fix.recordedAt,
    )
    val latest = if (visit.sameVisit) fixes(placeId).firstOrNull() else null
    when {
        latest == null -> {
            insertFix(entity)
            trimFixes(placeId, visit.keepFixes)
        }

        // The same visit keeps one fix, the most accurate.
        fix.accuracyMeters < latest.accuracyMeters -> {
            deleteFix(latest.id)
            insertFix(entity)
        }
    }
}

private suspend fun PlaceDao.mergeAccessPoints(placeId: Long, visit: VisitWrite) {
    if (visit.accessPoints.isEmpty()) return
    val stored = accessPoints(placeId).associateBy { it.bssid }
    upsertAccessPoints(
        visit.accessPoints.map { seen ->
            val known = stored[seen.bssid]
            PlaceAccessPointEntity(
                placeId = placeId,
                bssid = seen.bssid,
                ssid = seen.ssid,
                rssi = seen.rssi,
                seenCount = when {
                    known == null -> 1
                    visit.sameVisit -> known.seenCount
                    else -> known.seenCount + 1
                },
                lastSeenAt = visit.now,
            )
        },
    )
    trimAccessPoints(placeId, visit.keepAccessPoints)
}
