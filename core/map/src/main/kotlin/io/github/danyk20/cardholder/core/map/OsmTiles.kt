package io.github.danyk20.cardholder.core.map

import java.net.URL

/** One OpenStreetMap tile: [x] and [y] count from the top-left corner of the world at [zoom]. */
data class TileKey(val zoom: Int, val x: Int, val y: Int) {
    /** Where the tile lives in the cache, relative to its folder. */
    val path: String get() = "$zoom/$x/$y.png"
}

/** The OpenStreetMap tile server: where tiles come from and which requests may be made. */
object OsmTiles {
    const val HOST = "tile.openstreetmap.org"

    /** The tile at ([zoom], [x], [y]), or `null` if no such tile exists. */
    fun key(zoom: Int, x: Int, y: Int): TileKey? {
        if (zoom !in 0..WebMercator.MAX_ZOOM) return null
        val count = 1 shl zoom
        return if (x in 0 until count && y in 0 until count) TileKey(zoom, x, y) else null
    }

    /** The HTTPS address of [tile], in the form the tile server expects. */
    fun url(tile: TileKey): URL = URL("https://$HOST/${tile.zoom}/${tile.x}/${tile.y}.png")

    /**
     * The User-Agent of tile requests, which the tile usage policy requires to identify the app: the
     * installed [versionName], and the project's address to contact.
     */
    fun userAgent(versionName: String?): String =
        "Cardholder/${versionName ?: "unknown"} (+https://github.com/danyk20/cardholder)"

    /** Only plain HTTPS requests to the tile server are ever made; no other host or port. */
    fun isAllowed(url: URL): Boolean =
        url.protocol == "https" && url.host == HOST && url.port == -1 && url.userInfo == null
}
