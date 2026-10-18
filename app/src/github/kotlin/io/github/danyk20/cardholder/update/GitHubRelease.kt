package io.github.danyk20.cardholder.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The parts of GitHub's "latest release" response the updater needs. */
@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<Asset> = emptyList(),
) {
    @Serializable
    data class Asset(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
        val size: Long = 0,
        /** E.g. `sha256:9f86…`; GitHub publishes it for files uploaded since mid-2025. */
        val digest: String? = null,
    ) {
        val sha256: String? get() = digest?.removePrefix("sha256:")?.takeIf { digest.startsWith("sha256:") }
    }

    val version: String get() = tagName.removePrefix("v")

    /** The APK to install: `Cardholder-<version>.apk`, or the only APK of an older release. */
    fun apk(): Asset? = assets.firstOrNull { it.name == "Cardholder-$version.apk" }
        ?: assets.filter { it.name.endsWith(".apk") }.singleOrNull()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): GitHubRelease = json.decodeFromString(serializer(), text)
    }
}

/** Whether [candidate] (e.g. `1.4.0`) is a newer version than [installed] (e.g. `1.3.4`). */
internal fun isNewer(candidate: String, installed: String): Boolean {
    val new = candidate.versionParts() ?: return false
    val old = installed.versionParts() ?: return false
    for (i in 0 until maxOf(new.size, old.size)) {
        val difference = new.getOrElse(i) { 0 }.compareTo(old.getOrElse(i) { 0 })
        if (difference != 0) return difference > 0
    }
    return false
}

/** `1.3.4` → [1, 3, 4]; anything else (e.g. `1.4.0-beta`) isn't a release version. */
private fun String.versionParts(): List<Int>? =
    removePrefix("v").split('.').map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }
