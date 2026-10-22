package io.github.danyk20.cardholder.core.map

import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OsmTileStreamProviderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var now = Instant.parse("2026-10-01T10:00:00Z")
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId?) = this

        override fun instant(): Instant = now
    }
    private val fetcher = ScriptedFetcher()

    // Created lazily, as the temporary folder only exists once a test runs.
    private val cache by lazy { TileCache(File(folder.root, "map-tiles"), maxBytes = 10L * 1024 * 1024) }
    private val provider by lazy { OsmTileStreamProvider(cache, fetcher, clock) }

    @Test
    fun `a tile is fetched from the server and kept for next time`() = runTest {
        fetcher.next = TileFetch.Found(PNG_A)

        val stream = provider.getTileStream(row = 9, col = 8, zoomLvl = 4)

        assertEquals(PNG_A.toList(), stream?.readBytes()?.toList())
        assertEquals(listOf(TileKey(4, 8, 9)), fetcher.requested)
        assertEquals(PNG_A.size, cache.read(TileKey(4, 8, 9))?.png?.size)
    }

    @Test
    fun `a fresh tile is served from the cache without a request`() = runTest {
        fetcher.next = TileFetch.Found(PNG_A)
        provider.getTileStream(9, 8, 4)
        now = now.plus(Duration.ofDays(6))

        val stream = provider.getTileStream(9, 8, 4)

        assertEquals(PNG_A.toList(), stream?.readBytes()?.toList())
        assertEquals(1, fetcher.requested.size)
    }

    @Test
    fun `a tile older than seven days is fetched again`() = runTest {
        fetcher.next = TileFetch.Found(PNG_A)
        provider.getTileStream(9, 8, 4)
        now = now.plus(Duration.ofDays(7))
        fetcher.next = TileFetch.Found(PNG_B)

        val stream = provider.getTileStream(9, 8, 4)

        assertEquals(PNG_B.toList(), stream?.readBytes()?.toList())
        assertEquals(2, fetcher.requested.size)
        assertEquals(PNG_B.size, cache.read(TileKey(4, 8, 9))?.png?.size)
    }

    @Test
    fun `offline, an old tile is still shown and the map is told it's unreachable`() = runTest {
        fetcher.next = TileFetch.Found(PNG_A)
        provider.getTileStream(9, 8, 4)
        now = now.plus(Duration.ofDays(30))
        fetcher.next = TileFetch.Unreachable

        val stream = provider.getTileStream(9, 8, 4)

        assertEquals(PNG_A.toList(), stream?.readBytes()?.toList())
        assertTrue(provider.isUnreachable.value)
    }

    @Test
    fun `offline with no cached tile there is no tile and the map is told`() = runTest {
        fetcher.next = TileFetch.Unreachable

        assertNull(provider.getTileStream(9, 8, 4))

        assertTrue(provider.isUnreachable.value)
    }

    @Test
    fun `reaching the server again clears the unreachable state`() = runTest {
        fetcher.next = TileFetch.Unreachable
        provider.getTileStream(9, 8, 4)
        fetcher.next = TileFetch.Found(PNG_A)

        provider.getTileStream(1, 1, 1)

        assertFalse(provider.isUnreachable.value)
    }

    @Test
    fun `a refused answer keeps the old tile and isn't an offline state`() = runTest {
        fetcher.next = TileFetch.Found(PNG_A)
        provider.getTileStream(9, 8, 4)
        now = now.plus(Duration.ofDays(8))
        fetcher.next = TileFetch.Refused

        val stream = provider.getTileStream(9, 8, 4)

        assertEquals(PNG_A.toList(), stream?.readBytes()?.toList())
        assertFalse(provider.isUnreachable.value)
    }

    @Test
    fun `a refused answer for a tile never cached gives no tile`() = runTest {
        fetcher.next = TileFetch.Refused

        assertNull(provider.getTileStream(9, 8, 4))
        assertFalse(provider.isUnreachable.value)
    }

    @Test
    fun `a position outside the world is no tile and makes no request`() = runTest {
        assertNull(provider.getTileStream(row = 4, col = 0, zoomLvl = 2))
        assertNull(provider.getTileStream(row = 0, col = 0, zoomLvl = WebMercator.MAX_ZOOM + 1))

        assertTrue(fetcher.requested.isEmpty())
    }

    /** Answers with the next scripted fetch, and remembers which tiles were asked for. */
    private class ScriptedFetcher : TileFetcher {
        var next: TileFetch = TileFetch.Unreachable
        val requested = mutableListOf<TileKey>()

        override fun fetch(tile: TileKey): TileFetch {
            requested += tile
            return next
        }
    }

    private companion object {
        val PNG_A = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
        val PNG_B = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 9, 9, 9, 9)
    }
}
