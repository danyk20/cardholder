package io.github.danyk20.cardholder.core.data.repository

import io.github.danyk20.cardholder.core.data.model.toColumns
import io.github.danyk20.cardholder.core.data.model.toFix
import io.github.danyk20.cardholder.core.data.model.toPlace
import io.github.danyk20.cardholder.core.data.model.toWrite
import io.github.danyk20.cardholder.core.database.dao.PlaceDao
import io.github.danyk20.cardholder.core.database.model.PlaceWithAccessPoints
import io.github.danyk20.cardholder.core.domain.di.IoDispatcher
import io.github.danyk20.cardholder.core.domain.location.Bssid
import io.github.danyk20.cardholder.core.domain.location.GeoMath
import io.github.danyk20.cardholder.core.domain.location.Place
import io.github.danyk20.cardholder.core.domain.location.PlaceClustering
import io.github.danyk20.cardholder.core.domain.location.Surroundings
import io.github.danyk20.cardholder.core.domain.location.WifiSignals
import io.github.danyk20.cardholder.core.domain.repository.PlaceRepository
import io.github.danyk20.cardholder.core.model.CardId
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** [PlaceRepository] in the encrypted Room database; places never leave the device, not even in backups. */
@Singleton
internal class OfflinePlaceRepository @Inject constructor(
    private val dao: PlaceDao,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PlaceRepository {
    // Planning a visit reads the places it then changes, so visits are stored one at a time.
    private val writeMutex = Mutex()

    override suspend fun suggestionPlaces(): List<Place> = withContext(ioDispatcher) {
        dao.suggestionPlaces().map(PlaceWithAccessPoints::toPlace)
    }

    override fun observeGeofencePlaces(limit: Int): Flow<List<Place>> =
        dao.observeGeofencePlaces(limit).map { places -> places.map { it.toPlace() } }

    override suspend fun places(ids: Collection<Long>): List<Place> = withContext(ioDispatcher) {
        if (ids.isEmpty()) emptyList() else dao.places(ids).map(PlaceWithAccessPoints::toPlace)
    }

    override fun observeCount(cardId: CardId): Flow<Int> = dao.observeCount(cardId.value)

    override fun observeTotal(): Flow<Int> = dao.observeTotal()

    override suspend fun recordVisit(cardId: CardId, surroundings: Surroundings): Boolean = withContext(ioDispatcher) {
        writeMutex.withLock {
            val now = clock.instant()
            val scanned = surroundings.accessPoints.map { it.bssid.value }
            val plan = PlaceClustering.plan(
                cardPlaces = dao.placesOf(cardId.value).map(PlaceWithAccessPoints::toPlace),
                knownPlaces = if (scanned.isEmpty()) {
                    emptyList()
                } else {
                    dao.placesWithAccessPoints(scanned).map(PlaceWithAccessPoints::toPlace)
                },
                surroundings = surroundings,
                travelling = dao.travelling().mapTo(HashSet(), ::Bssid),
                now = now,
            ) ?: return@withLock false
            val write = plan.toWrite(cardId, now, PlaceClustering.MAX_FIXES, WifiSignals.MAX_STORED)
            dao.applyVisit(write) { fixes -> GeoMath.centre(fixes.map { it.toFix() })?.toColumns() }
            true
        }
    }

    override suspend fun markNotified(placeId: Long) = withContext(ioDispatcher) {
        dao.markNotified(placeId, clock.millis())
    }

    override suspend fun lastNotifiedAt(cardId: CardId?): Instant? = withContext(ioDispatcher) {
        val millis = if (cardId == null) dao.lastNotifiedAt() else dao.lastNotifiedAt(cardId.value)
        millis?.let(Instant::ofEpochMilli)
    }

    override suspend fun forget(placeId: Long) = withContext(ioDispatcher) { dao.delete(placeId) }

    override suspend fun forgetCard(cardId: CardId) = withContext(ioDispatcher) { dao.deleteForCard(cardId.value) }

    override suspend fun forgetAll() = withContext(ioDispatcher) { dao.forgetAll() }
}
