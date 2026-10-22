package io.github.danyk20.cardholder.core.map

import android.content.Context
import java.io.File
import java.time.Clock

/** The one tile supply of the app, so every map shares the cache and the download limit. */
internal object MapTiles {
    /** The cache is capped at about 50 MB, in the app's cache folder, so Android may clear it. */
    const val MAX_CACHE_BYTES = 50L * 1024 * 1024
    private const val CACHE_FOLDER = "map-tiles"

    private var instance: OsmTileStreamProvider? = null

    @Synchronized
    fun get(context: Context): OsmTileStreamProvider =
        instance ?: create(context.applicationContext).also { instance = it }

    private fun create(context: Context): OsmTileStreamProvider = OsmTileStreamProvider(
        cache = TileCache(File(context.cacheDir, CACHE_FOLDER), MAX_CACHE_BYTES),
        fetcher = HttpTileFetcher(HttpsTileConnector, OsmTiles.userAgent(versionNameOf(context))),
        clock = Clock.systemUTC(),
    )

    @Suppress("DEPRECATION") // The non-flags overload works on every supported Android version.
    private fun versionNameOf(context: Context): String? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()
}
