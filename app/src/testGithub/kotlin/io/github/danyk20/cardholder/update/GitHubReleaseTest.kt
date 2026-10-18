package io.github.danyk20.cardholder.update

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class GitHubReleaseTest {
    private val json = """
        {
          "tag_name": "v1.4.0",
          "name": "Cardholder v1.4.0",
          "draft": false,
          "prerelease": false,
          "body": "### Added\n- Updates from the app",
          "assets": [
            {
              "name": "Cardholder-1.4.0.aab",
              "browser_download_url": "https://github.com/danyk20/cardholder/releases/download/v1.4.0/Cardholder-1.4.0.aab",
              "size": 23000000
            },
            {
              "name": "Cardholder-1.4.0.apk",
              "browser_download_url": "https://github.com/danyk20/cardholder/releases/download/v1.4.0/Cardholder-1.4.0.apk",
              "size": 36000000,
              "digest": "sha256:ABC123",
              "uploader": { "login": "github-actions[bot]" }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `reads the version, notes and the APK with its digest`() {
        val release = GitHubRelease.parse(json)

        assertEquals("1.4.0", release.version)
        assertEquals("### Added\n- Updates from the app", release.body)
        val apk = release.apk()!!
        assertEquals("Cardholder-1.4.0.apk", apk.name)
        assertEquals(36_000_000, apk.size)
        assertEquals("ABC123", apk.sha256)
    }

    @Test
    fun `older releases with differently named files still find their only APK`() {
        val release = GitHubRelease(
            tagName = "v1.3.0",
            assets = listOf(
                GitHubRelease.Asset("app-release.aab", "https://github.com/a.aab"),
                GitHubRelease.Asset("app-release.apk", "https://github.com/a.apk"),
            ),
        )

        assertEquals("app-release.apk", release.apk()?.name)
        assertNull(release.apk()?.sha256)
        assertNull(GitHubRelease(tagName = "v1.0.0").apk())
    }

    @Test
    fun `compares versions numerically`() {
        assertTrue(isNewer("1.4.0", "1.3.4"))
        assertTrue(isNewer("1.10.0", "1.9.9"))
        assertTrue(isNewer("v2.0", "1.99.99"))
        assertFalse(isNewer("1.3.4", "1.3.4"))
        assertFalse(isNewer("1.3.3", "1.3.4"))
        assertFalse(isNewer("1.4.0", "1.4"))
        // Pre-releases and odd tags are never offered.
        assertFalse(isNewer("1.5.0-beta", "1.4.0"))
        assertFalse(isNewer("latest", "1.4.0"))
    }

    @Test
    fun `only contacts GitHub over HTTPS`() {
        assertTrue(GitHubUpdateSource.isAllowed("https://api.github.com/repos/danyk20/cardholder/releases/latest"))
        assertTrue(GitHubUpdateSource.isAllowed("https://release-assets.githubusercontent.com/x/Cardholder.apk"))
        assertFalse(GitHubUpdateSource.isAllowed("http://github.com/danyk20/cardholder"))
        assertFalse(GitHubUpdateSource.isAllowed("https://github.com.evil.example/x.apk"))
        assertFalse(GitHubUpdateSource.isAllowed("https://user@github.com/x.apk"))
        assertFalse(GitHubUpdateSource.isAllowed("https://github.com:8443/x.apk"))
    }
}
