package io.github.danyk20.cardholder.core.data.repository

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class HttpsLogoDownloaderTest {
    @Test
    fun `only https wikimedia media hosts are allowed`() {
        assertTrue(HttpsLogoDownloader.isAllowed("https://upload.wikimedia.org/wikipedia/commons/a/b/logo.png"))
        assertTrue(
            HttpsLogoDownloader.isAllowed("https://thumb.wikimedia.org/wikipedia/commons/thumb/x.svg/960px-x.svg.png"),
        )
        assertFalse(HttpsLogoDownloader.isAllowed("http://upload.wikimedia.org/logo.png"))
        assertFalse(HttpsLogoDownloader.isAllowed("https://evil.example/logo.png"))
        assertFalse(HttpsLogoDownloader.isAllowed("https://upload.wikimedia.org.evil.example/logo.png"))
        assertFalse(HttpsLogoDownloader.isAllowed("https://user@upload.wikimedia.org/logo.png"))
        assertFalse(HttpsLogoDownloader.isAllowed("https://upload.wikimedia.org:8443/logo.png"))
        assertFalse(HttpsLogoDownloader.isAllowed("not a url"))
    }
}
