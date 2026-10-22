package io.github.danyk20.cardholder.core.map

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.URL

/** What the tile server answered for one tile. */
sealed interface TileFetch {
    /** A valid PNG tile. */
    data class Found(val png: ByteArray) : TileFetch

    /** The server answered, but not with a PNG tile in the expected shape. */
    data object Refused : TileFetch

    /** No answer: offline, a timeout or a broken connection. */
    data object Unreachable : TileFetch
}

/** Gets one tile from the server; blocking, so it's called off the main thread. */
interface TileFetcher {
    fun fetch(tile: TileKey): TileFetch
}

/** One HTTP response, as much of it as getting a tile needs. */
interface TileResponse : Closeable {
    val code: Int
    val contentType: String?
    val body: InputStream
}

/** Opens a GET request for [url] with the given [userAgent]; the only place a connection is made. */
fun interface TileConnector {
    @Throws(IOException::class)
    fun open(url: URL, userAgent: String): TileResponse
}

/**
 * Fetches tiles through [connector], accepting only what the tile policy allows: an HTTPS request to the
 * tile server, a 200 answer that is a PNG of at most [MAX_BYTES], and no redirects.
 */
class HttpTileFetcher(private val connector: TileConnector, private val userAgent: String) : TileFetcher {
    override fun fetch(tile: TileKey): TileFetch {
        val url = OsmTiles.url(tile)
        if (!OsmTiles.isAllowed(url)) return TileFetch.Refused
        return try {
            connector.open(url, userAgent).use(::readTile)
        } catch (@Suppress("SwallowedException") e: IOException) {
            // No answer is an outcome of its own, not an error to report.
            TileFetch.Unreachable
        }
    }

    private fun readTile(response: TileResponse): TileFetch {
        val isPng = response.contentType?.substringBefore(';')?.trim()?.equals(PNG_TYPE, ignoreCase = true) == true
        if (response.code != HTTP_OK || !isPng) return TileFetch.Refused
        val png = response.body.use { it.readAtMost(MAX_BYTES) } ?: return TileFetch.Refused
        return if (png.startsWithPngSignature()) TileFetch.Found(png) else TileFetch.Refused
    }

    /** The bytes of the stream, or `null` as soon as it's longer than [limit]. */
    private fun InputStream.readAtMost(limit: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) return output.toByteArray()
            total += read
            if (total > limit) return null
            output.write(buffer, 0, read)
        }
    }

    private fun ByteArray.startsWithPngSignature(): Boolean =
        size >= PNG_SIGNATURE.size && PNG_SIGNATURE.indices.all { this[it] == PNG_SIGNATURE[it] }

    companion object {
        const val MAX_BYTES = 512 * 1024
        const val HTTP_OK = 200
        const val PNG_TYPE = "image/png"
        private const val BUFFER_SIZE = 8 * 1024
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}
