package io.github.danyk20.cardholder.update

import android.app.Activity
import android.content.Context
import android.util.Log
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.danyk20.cardholder.BuildConfig
import io.github.danyk20.cardholder.core.domain.di.IoDispatcher
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

/**
 * Updates from the app's GitHub releases: reads the latest release, downloads its APK and hands it to
 * Android's installer. HTTPS only, restricted to GitHub's hosts, size-limited, and the file must match
 * the SHA-256 GitHub publishes for it.
 */
@Singleton
class GitHubUpdateSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UpdateSource {
    private var latest: GitHubRelease? = null

    override val channel = UpdateChannel.GITHUB

    override val unfinishedInstalls: Flow<InstallOutcome> = ApkInstaller.unfinished

    override suspend fun check(): UpdateCheck = withContext(ioDispatcher) {
        try {
            val release = GitHubRelease.parse(get(LATEST_RELEASE_URL).use { it.inputStream.reader().readText() })
            latest = release
            if (!release.draft && !release.prerelease && release.apk() != null &&
                isNewer(release.version, BuildConfig.VERSION_NAME)
            ) {
                UpdateCheck.Available(version = release.version, notes = release.body?.takeIf(String::isNotBlank))
            } else {
                UpdateCheck.UpToDate
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Update check failed", e)
            UpdateCheck.Failed
        } catch (e: SerializationException) {
            Log.w(TAG, "Unexpected release data", e)
            UpdateCheck.Failed
        }
    }

    override suspend fun install(activity: Activity, onProgress: (Float) -> Unit): InstallResult {
        val apk = latest?.apk() ?: return InstallResult.Failed
        // Ask before downloading 30+ MB that couldn't be installed anyway.
        if (!context.packageManager.canRequestPackageInstalls()) return InstallResult.NeedsPermission
        return withContext(ioDispatcher) {
            try {
                val file = download(apk, onProgress) ?: return@withContext InstallResult.Failed
                ApkInstaller.install(context, file)
                InstallResult.Started
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "Update download failed", e)
                InstallResult.Failed
            }
        }
    }

    /** Downloads [asset] to the cache; `null` if it's too big or doesn't match its published size or digest. */
    private suspend fun download(asset: GitHubRelease.Asset, onProgress: (Float) -> Unit): File? {
        if (asset.size > MAX_APK_BYTES) return null
        val directory = File(context.cacheDir, UPDATES_DIR).apply {
            deleteRecursively()
            mkdirs()
        }
        val file = File(directory, "update.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        get(asset.downloadUrl).use { connection ->
            val total = asset.size.takeIf { it > 0 } ?: connection.contentLengthLong
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var written = 0L
                    var read = input.read(buffer)
                    while (read >= 0) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        written += read
                        if (written > MAX_APK_BYTES) return null
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        if (total > 0) onProgress((written.toFloat() / total).coerceAtMost(1f))
                        read = input.read(buffer)
                    }
                }
            }
        }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        val matches = (asset.size <= 0 || file.length() == asset.size) &&
            (asset.sha256 == null || asset.sha256.equals(sha256, ignoreCase = true))
        return file.takeIf { matches } ?: run {
            file.delete()
            null
        }
    }

    /** Opens [url], following redirects only to GitHub's own hosts. Close it with [use]. */
    private fun get(url: String): HttpsURLConnection {
        var target = url
        repeat(MAX_REDIRECTS + 1) {
            if (!isAllowed(target)) throw IOException("Not a GitHub URL: $target")
            val connection = URL(target).openConnection() as HttpsURLConnection
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty(
                "Accept",
                if (target == url &&
                    url == LATEST_RELEASE_URL
                ) {
                    GITHUB_JSON
                } else {
                    "*/*"
                },
            )
            when (connection.responseCode) {
                HttpsURLConnection.HTTP_OK -> return connection

                in REDIRECT_CODES -> {
                    target = connection.getHeaderField("Location") ?: throw IOException("Redirect without location")
                    connection.disconnect()
                }

                else -> {
                    connection.disconnect()
                    throw IOException("HTTP ${connection.responseCode} for $target")
                }
            }
        }
        throw IOException("Too many redirects")
    }

    private inline fun <T> HttpsURLConnection.use(block: (HttpsURLConnection) -> T): T = try {
        block(this)
    } finally {
        disconnect()
    }

    companion object {
        private const val TAG = "Updates"
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/danyk20/cardholder/releases/latest"
        private const val GITHUB_JSON = "application/vnd.github+json"
        private const val USER_AGENT = "Cardholder (https://github.com/danyk20/cardholder)"
        private const val UPDATES_DIR = "updates"
        private const val TIMEOUT_MILLIS = 15_000
        private const val MAX_REDIRECTS = 3
        private const val MAX_APK_BYTES = 150L * 1024 * 1024
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private val ALLOWED_HOSTS = setOf(
            "api.github.com",
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
        )

        /** Only HTTPS URLs on GitHub's API, release and download hosts are ever requested. */
        internal fun isAllowed(url: String): Boolean = runCatching { URL(url) }.getOrNull()
            ?.let { it.protocol == "https" && it.host in ALLOWED_HOSTS && it.userInfo == null && it.port == -1 } == true
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface GitHubUpdateModule {
    @Binds
    fun bindUpdateSource(source: GitHubUpdateSource): UpdateSource
}
