package io.github.danyk20.cardholder.core.data.repository

import android.util.Log
import io.github.danyk20.cardholder.core.domain.di.IoDispatcher
import io.github.danyk20.cardholder.core.domain.repository.LogoDownloader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Downloads official shop logos from Wikimedia Commons. HTTPS only, restricted to the Commons media
 * hosts, size-limited and image-only, so the app's single network feature can't be used to reach
 * anything else.
 */
internal class HttpsLogoDownloader @Inject constructor(@IoDispatcher private val ioDispatcher: CoroutineDispatcher) :
    LogoDownloader {
    override suspend fun download(url: String): ByteArray? = withContext(ioDispatcher) {
        if (!isAllowed(url)) return@withContext null
        try {
            fetch(url)
        } catch (e: IOException) {
            Log.w(TAG, "Logo download failed", e)
            null
        }
    }

    private fun fetch(url: String): ByteArray? {
        val connection = URL(url).openConnection() as HttpsURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val isImage = connection.contentType?.startsWith("image/") == true
            if (connection.responseCode != HttpsURLConnection.HTTP_OK || !isImage) return null
            connection.inputStream.use { it.readAtMost(MAX_BYTES) }
        } finally {
            connection.disconnect()
        }
    }

    /** Reads the stream, or returns `null` as soon as it exceeds [limit] bytes. */
    private fun InputStream.readAtMost(limit: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var read = read(buffer)
        while (read >= 0) {
            if (output.size() + read > limit) return null
            output.write(buffer, 0, read)
            read = read(buffer)
        }
        return output.toByteArray()
    }

    companion object {
        private const val TAG = "LogoDownloader"
        private const val TIMEOUT_MILLIS = 10_000
        private const val MAX_BYTES = 2 * 1024 * 1024
        private const val USER_AGENT = "Cardholder (https://github.com/danyk20/cardholder)"
        private val ALLOWED_HOSTS = setOf("upload.wikimedia.org", "thumb.wikimedia.org")

        /** Only HTTPS URLs on the Wikimedia media hosts are ever requested. */
        fun isAllowed(url: String): Boolean = runCatching { URL(url) }.getOrNull()
            ?.let { it.protocol == "https" && it.host in ALLOWED_HOSTS && it.userInfo == null && it.port == -1 } == true
    }
}
