package io.github.danyk20.cardholder.core.map

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.Test

class HttpTileFetcherTest {
    private val tile = TileKey(4, 8, 9)
    private var opened: Pair<URL, String>? = null

    @Test
    fun `a PNG answered with 200 is the tile`() {
        val png = pngBytes(200)

        val fetch = fetcherReturning(FakeResponse(200, "image/png", png)).fetch(tile)

        assertEquals(png.toList(), assertIs<TileFetch.Found>(fetch).png.toList())
    }

    @Test
    fun `the content type may carry parameters and any case`() {
        val fetch = fetcherReturning(FakeResponse(200, "Image/PNG; charset=binary", pngBytes(20))).fetch(tile)

        assertIs<TileFetch.Found>(fetch)
    }

    @Test
    fun `the request goes to the tile server with the app's user agent`() {
        fetcherReturning(FakeResponse(200, "image/png", pngBytes(20))).fetch(tile)

        assertEquals("https://tile.openstreetmap.org/4/8/9.png", opened?.first.toString())
        assertEquals("Cardholder/1.6.0 (+https://github.com/danyk20/cardholder)", opened?.second)
    }

    @Test
    fun `a redirect is refused`() {
        val fetch = fetcherReturning(FakeResponse(302, "image/png", pngBytes(20))).fetch(tile)

        assertEquals(TileFetch.Refused, fetch)
    }

    @Test
    fun `an error answer is refused`() {
        listOf(301, 404, 429, 500, 503).forEach { code ->
            assertEquals(TileFetch.Refused, fetcherReturning(FakeResponse(code, "image/png", pngBytes(20))).fetch(tile))
        }
    }

    @Test
    fun `a page or any other type than PNG is refused`() {
        listOf("text/html", "image/jpeg", "application/octet-stream", null).forEach { type ->
            assertEquals(
                TileFetch.Refused,
                fetcherReturning(FakeResponse(200, type, pngBytes(20))).fetch(tile),
                "$type",
            )
        }
    }

    @Test
    fun `a body that isn't really a PNG is refused`() {
        val notPng = "<html>not a tile</html>".toByteArray()

        assertEquals(TileFetch.Refused, fetcherReturning(FakeResponse(200, "image/png", notPng)).fetch(tile))
        assertEquals(TileFetch.Refused, fetcherReturning(FakeResponse(200, "image/png", ByteArray(0))).fetch(tile))
    }

    @Test
    fun `a tile larger than the limit is refused`() {
        val oversized = pngBytes(HttpTileFetcher.MAX_BYTES + 1)

        assertEquals(TileFetch.Refused, fetcherReturning(FakeResponse(200, "image/png", oversized)).fetch(tile))
    }

    @Test
    fun `a tile exactly at the limit is accepted`() {
        val atLimit = pngBytes(HttpTileFetcher.MAX_BYTES)

        assertIs<TileFetch.Found>(fetcherReturning(FakeResponse(200, "image/png", atLimit)).fetch(tile))
    }

    @Test
    fun `no answer at all is unreachable`() {
        val fetcher = HttpTileFetcher(TileConnector { _, _ -> throw IOException("offline") }, USER_AGENT)

        assertEquals(TileFetch.Unreachable, fetcher.fetch(tile))
    }

    @Test
    fun `a connection that breaks while the tile is read is unreachable`() {
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("connection reset")
        }

        val fetch = fetcherReturning(FakeResponse(200, "image/png", body = broken)).fetch(tile)

        assertEquals(TileFetch.Unreachable, fetch)
    }

    private fun fetcherReturning(response: FakeResponse) = HttpTileFetcher(
        connector = TileConnector { url, userAgent ->
            opened = url to userAgent
            response
        },
        userAgent = USER_AGENT,
    )

    private class FakeResponse(
        override val code: Int,
        override val contentType: String?,
        bytes: ByteArray = ByteArray(0),
        body: InputStream? = null,
    ) : TileResponse {
        override val body: InputStream = body ?: ByteArrayInputStream(bytes)
        var closed = false
            private set

        override fun close() {
            closed = true
        }
    }

    private fun pngBytes(size: Int): ByteArray {
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        return signature + ByteArray(size - signature.size) { 7 }
    }

    private companion object {
        const val USER_AGENT = "Cardholder/1.6.0 (+https://github.com/danyk20/cardholder)"
    }
}
