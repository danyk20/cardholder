package io.github.danyk20.cardholder.core.map

import java.io.File
import java.io.IOException
import java.time.Instant

/** A tile read from the cache, with the time it was fetched from the server. */
class CachedTile(val png: ByteArray, val fetchedAt: Instant)

/**
 * Tiles kept as PNG files in [directory]. Once they take more than [maxBytes], the least recently used
 * tiles are deleted. A tile's fetch time is its file's last-modified time, so it survives restarts;
 * the usage order is kept in memory and starts from the fetch times. Thread-safe.
 */
class TileCache(private val directory: File, private val maxBytes: Long) {
    private val entries = LinkedHashMap<TileKey, Entry>(INITIAL_CAPACITY, LOAD_FACTOR, true)
    private var totalBytes = 0L
    private var loaded = false

    /** The tile at [key], marking it as recently used; `null` if it isn't cached. */
    @Synchronized
    fun read(key: TileKey): CachedTile? {
        ensureLoaded()
        val entry = entries[key] ?: return null
        val png = try {
            file(key).readBytes()
        } catch (@Suppress("SwallowedException") e: IOException) {
            // A tile that can't be read any more is as good as missing.
            remove(key)
            return null
        }
        return CachedTile(png, entry.fetchedAt)
    }

    /** Stores [png] as the tile at [key], fetched at [fetchedAt], then evicts the least recently used. */
    @Synchronized
    fun write(key: TileKey, png: ByteArray, fetchedAt: Instant) {
        ensureLoaded()
        val target = file(key)
        try {
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeBytes(png)
            temp.setLastModified(fetchedAt.toEpochMilli())
            // Replace the file in one step, so a tile is never read half-written.
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        } catch (@Suppress("SwallowedException") e: IOException) {
            // Without the cache the tile is still shown, it just has to be fetched again next time.
            target.delete()
            return
        }
        // The file was replaced in place, so only the bookkeeping of the old copy goes.
        entries.remove(key)?.let { totalBytes -= it.size }
        entries[key] = Entry(png.size.toLong(), fetchedAt)
        totalBytes += png.size
        evict()
    }

    private fun evict() {
        val oldest = entries.entries.iterator()
        while (totalBytes > maxBytes && oldest.hasNext()) {
            val (key, entry) = oldest.next()
            oldest.remove()
            totalBytes -= entry.size
            file(key).delete()
        }
    }

    private fun remove(key: TileKey) {
        val entry = entries.remove(key) ?: return
        totalBytes -= entry.size
        file(key).delete()
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!directory.isDirectory) return
        directory.walkTopDown()
            .filter { it.isFile && it.name.endsWith(PNG_SUFFIX) }
            .mapNotNull { file -> keyOf(file)?.let { key -> key to file } }
            .sortedBy { (_, file) -> file.lastModified() }
            .forEach { (key, file) ->
                entries[key] = Entry(file.length(), Instant.ofEpochMilli(file.lastModified()))
                totalBytes += file.length()
            }
        evict()
    }

    private fun keyOf(file: File): TileKey? {
        val parts = file.relativeTo(directory).invariantSeparatorsPath.removeSuffix(PNG_SUFFIX).split('/')
        if (parts.size != TILE_PARTS) return null
        val (zoom, x, y) = parts.map { it.toIntOrNull() ?: return null }
        return OsmTiles.key(zoom, x, y)
    }

    private fun file(key: TileKey) = File(directory, key.path)

    private class Entry(val size: Long, val fetchedAt: Instant)

    private companion object {
        const val PNG_SUFFIX = ".png"
        const val TILE_PARTS = 3
        const val INITIAL_CAPACITY = 64
        const val LOAD_FACTOR = 0.75f
    }
}
