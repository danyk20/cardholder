package io.github.danyk20.cardholder.core.map

import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class OsmTilesTest {
    @Test
    fun `a tile's address follows the tile server's zoom, x and y layout`() {
        assertEquals(
            "https://tile.openstreetmap.org/17/71340/46478.png",
            OsmTiles.url(TileKey(17, 71340, 46478)).toString(),
        )
    }

    @Test
    fun `a tile is stored under its zoom, x and y`() {
        assertEquals("17/71340/46478.png", TileKey(17, 71340, 46478).path)
    }

    @Test
    fun `only tiles that exist at their zoom level are keys`() {
        assertEquals(TileKey(0, 0, 0), OsmTiles.key(0, 0, 0))
        assertEquals(TileKey(2, 3, 3), OsmTiles.key(2, 3, 3))
        assertNull(OsmTiles.key(2, 4, 0))
        assertNull(OsmTiles.key(2, 0, -1))
        assertNull(OsmTiles.key(-1, 0, 0))
        assertNull(OsmTiles.key(WebMercator.MAX_ZOOM + 1, 0, 0))
    }

    @Test
    fun `only plain https requests to the tile server are allowed`() {
        assertTrue(OsmTiles.isAllowed(URL("https://tile.openstreetmap.org/1/0/0.png")))
        assertFalse(OsmTiles.isAllowed(URL("http://tile.openstreetmap.org/1/0/0.png")))
        assertFalse(OsmTiles.isAllowed(URL("https://tile.openstreetmap.org:8443/1/0/0.png")))
        assertFalse(OsmTiles.isAllowed(URL("https://user@tile.openstreetmap.org/1/0/0.png")))
        assertFalse(OsmTiles.isAllowed(URL("https://example.com/1/0/0.png")))
    }

    @Test
    fun `the user agent names the app version and the project`() {
        assertEquals(
            "Cardholder/1.6.0 (+https://github.com/danyk20/cardholder)",
            OsmTiles.userAgent("1.6.0"),
        )
        assertEquals(
            "Cardholder/unknown (+https://github.com/danyk20/cardholder)",
            OsmTiles.userAgent(null),
        )
    }
}
