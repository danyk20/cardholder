package io.github.danyk20.cardholder.core.map

import java.io.IOException
import java.io.InputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Opens tile requests over HTTPS with short timeouts. Redirects aren't followed, and no cookie store is
 * set, so the server sees only the request for the tile and the app's User-Agent.
 */
internal object HttpsTileConnector : TileConnector {
    private const val TIMEOUT_MILLIS = 10_000

    override fun open(url: URL, userAgent: String): TileResponse {
        val connection = url.openConnection() as HttpsURLConnection
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.setRequestProperty("User-Agent", userAgent)
        connection.setRequestProperty("Accept", "image/png")
        return try {
            HttpsTileResponse(connection)
        } catch (e: IOException) {
            connection.disconnect()
            throw e
        }
    }

    private class HttpsTileResponse(private val connection: HttpsURLConnection) : TileResponse {
        override val code: Int = connection.responseCode
        override val contentType: String? = connection.contentType
        override val body: InputStream get() = connection.inputStream

        override fun close() = connection.disconnect()
    }
}
