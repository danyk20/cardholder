package io.github.danyk20.cardholder.core.map

import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TileCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val fetchedAt = Instant.parse("2026-10-01T10:00:00Z")

    private fun cache(maxBytes: Long = 1_000) = TileCache(File(folder.root, "map-tiles"), maxBytes)

    @Test
    fun `a stored tile is read back with its fetch time`() {
        val cache = cache()

        cache.write(TileKey(3, 4, 5), png(100), fetchedAt)

        val tile = assertNotNull(cache.read(TileKey(3, 4, 5)))
        assertEquals(100, tile.png.size)
        assertEquals(fetchedAt, tile.fetchedAt)
    }

    @Test
    fun `a tile that isn't cached reads as nothing`() {
        assertNull(cache().read(TileKey(3, 4, 5)))
    }

    @Test
    fun `tiles are stored under their zoom, x and y`() {
        cache().write(TileKey(3, 4, 5), png(10), fetchedAt)

        assertTrue(File(folder.root, "map-tiles/3/4/5.png").isFile)
    }

    @Test
    fun `once the cache is full the least recently used tile goes first`() {
        val cache = cache(maxBytes = 250)
        cache.write(TileKey(1, 0, 0), png(100), fetchedAt)
        cache.write(TileKey(1, 0, 1), png(100), fetchedAt)
        assertNotNull(cache.read(TileKey(1, 0, 0)))

        cache.write(TileKey(1, 1, 1), png(100), fetchedAt)

        assertNotNull(cache.read(TileKey(1, 0, 0)), "recently read, so kept")
        assertNull(cache.read(TileKey(1, 0, 1)), "least recently used, so evicted")
        assertNotNull(cache.read(TileKey(1, 1, 1)))
        assertFalse(File(folder.root, "map-tiles/1/0/1.png").exists())
    }

    @Test
    fun `the cache never grows beyond its size cap`() {
        val cache = cache(maxBytes = 350)

        repeat(20) { index -> cache.write(TileKey(5, index, 0), png(100), fetchedAt) }

        val total = folder.root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        assertTrue(total <= 350, "cache holds $total bytes")
        assertNotNull(cache.read(TileKey(5, 19, 0)))
        assertNull(cache.read(TileKey(5, 0, 0)))
    }

    @Test
    fun `rewriting a tile replaces it and its size`() {
        val cache = cache(maxBytes = 250)
        cache.write(TileKey(1, 0, 0), png(100), fetchedAt)
        cache.write(TileKey(1, 0, 0), png(150), fetchedAt.plusSeconds(60))

        val tile = assertNotNull(cache.read(TileKey(1, 0, 0)))
        assertEquals(150, tile.png.size)
        assertEquals(fetchedAt.plusSeconds(60), tile.fetchedAt)
        cache.write(TileKey(1, 0, 1), png(90), fetchedAt)
        assertNotNull(cache.read(TileKey(1, 0, 0)), "150 + 90 bytes fit the 250 byte cap")
    }

    @Test
    fun `a new cache finds the tiles the last one stored, with their fetch times`() {
        cache().write(TileKey(2, 1, 1), png(40), fetchedAt)

        val reopened = cache()

        val tile = assertNotNull(reopened.read(TileKey(2, 1, 1)))
        assertEquals(40, tile.png.size)
        assertEquals(fetchedAt, tile.fetchedAt)
    }

    @Test
    fun `a reopened cache still evicts the oldest tiles first`() {
        cache(maxBytes = 1_000).apply {
            write(TileKey(1, 0, 0), png(100), fetchedAt)
            write(TileKey(1, 0, 1), png(100), fetchedAt.plusSeconds(3_600))
        }

        val reopened = cache(maxBytes = 150)
        reopened.write(TileKey(1, 1, 1), png(40), fetchedAt.plusSeconds(7_200))

        assertNull(reopened.read(TileKey(1, 0, 0)))
        assertNotNull(reopened.read(TileKey(1, 0, 1)))
    }

    @Test
    fun `files that aren't tiles are left alone`() {
        val root = File(folder.root, "map-tiles").apply { mkdirs() }
        File(root, "notes.txt").writeText("not a tile")
        File(root, "x/y/z.png").apply {
            parentFile?.mkdirs()
            writeBytes(png(10))
        }

        val cache = cache(maxBytes = 1_000)

        assertNull(cache.read(TileKey(0, 0, 0)))
        assertTrue(File(root, "notes.txt").exists())
    }

    @Test
    fun `a cache that can't be written to keeps working without the tile`() {
        val blocked = File(folder.root, "blocked").apply { writeText("a file, not a folder") }
        val cache = TileCache(blocked, maxBytes = 1_000)

        cache.write(TileKey(1, 0, 0), png(10), fetchedAt)

        assertNull(cache.read(TileKey(1, 0, 0)))
    }

    private fun png(size: Int) = ByteArray(size) { (it % 7).toByte() }
}
