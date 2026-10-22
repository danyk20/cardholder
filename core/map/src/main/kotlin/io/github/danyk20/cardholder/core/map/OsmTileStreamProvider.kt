package io.github.danyk20.cardholder.core.map

import io.github.danyk20.cardholder.core.map.TileFetch.Found
import io.github.danyk20.cardholder.core.map.TileFetch.Refused
import io.github.danyk20.cardholder.core.map.TileFetch.Unreachable
import java.io.InputStream
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import ovh.plrapps.mapcompose.core.TileStreamProvider

/**
 * Supplies the OpenStreetMap tiles to the map: a cached tile is used while it's younger than [maxAge];
 * otherwise it's fetched again, at most [MAX_PARALLEL_DOWNLOADS] at a time. A tile that can't be fetched
 * falls back to its stale cached copy, or to no tile at all.
 */
class OsmTileStreamProvider(
    private val cache: TileCache,
    private val fetcher: TileFetcher,
    private val clock: Clock,
    private val maxAge: Duration = MAX_AGE,
) : TileStreamProvider {
    private val downloads = Semaphore(MAX_PARALLEL_DOWNLOADS)
    private val unreachable = MutableStateFlow(false)

    /** Whether the last tile that had to be fetched couldn't be reached, e.g. when offline. */
    val isUnreachable: StateFlow<Boolean> = unreachable.asStateFlow()

    override suspend fun getTileStream(row: Int, col: Int, zoomLvl: Int): InputStream? {
        // MapCompose counts rows from the top and columns from the left, as the tile server does.
        val tile = OsmTiles.key(zoom = zoomLvl, x = col, y = row) ?: return null
        val cached = withContext(Dispatchers.IO) { cache.read(tile) }
        if (cached != null && Duration.between(cached.fetchedAt, clock.instant()) < maxAge) {
            return cached.png.inputStream()
        }
        return when (val fetch = downloads.withPermit { withContext(Dispatchers.IO) { fetcher.fetch(tile) } }) {
            is Found -> {
                withContext(Dispatchers.IO) { cache.write(tile, fetch.png, clock.instant()) }
                unreachable.value = false
                fetch.png.inputStream()
            }

            Refused -> cached?.png?.inputStream()

            Unreachable -> {
                unreachable.value = true
                cached?.png?.inputStream()
            }
        }
    }

    companion object {
        const val MAX_PARALLEL_DOWNLOADS = 2
        val MAX_AGE: Duration = Duration.ofDays(7)
    }
}
