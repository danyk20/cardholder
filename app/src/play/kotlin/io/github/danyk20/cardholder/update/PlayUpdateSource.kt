package io.github.danyk20.cardholder.update

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Updates through Google Play's in-app updates: Play checks, downloads and installs (full screen), so
 * this build never downloads or installs anything itself, as Google Play's policy requires.
 */
@Singleton
class PlayUpdateSource @Inject constructor(@ApplicationContext context: Context) : UpdateSource {
    private val manager = AppUpdateManagerFactory.create(context)
    private var lastInfo: AppUpdateInfo? = null

    override val channel = UpdateChannel.GOOGLE_PLAY

    @Suppress("TooGenericExceptionCaught") // E.g. no Play Store, or the app wasn't installed from Play.
    override suspend fun check(): UpdateCheck = try {
        val info = manager.requestAppUpdateInfo()
        lastInfo = info
        val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
            info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
        if (available && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            // Play only reports a version code, not the name users know.
            UpdateCheck.Available(version = null, notes = null)
        } else {
            UpdateCheck.UpToDate
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Google Play update check failed", e)
        UpdateCheck.Failed
    }

    override suspend fun install(activity: Activity, onProgress: (Float) -> Unit): InstallResult {
        val info = lastInfo ?: return InstallResult.Failed
        val started = manager.startUpdateFlow(info, activity, AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE))
        return if (started.isComplete && !started.isSuccessful) InstallResult.Failed else InstallResult.Started
    }

    private companion object {
        const val TAG = "PlayUpdates"
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface PlayUpdateModule {
    @Binds
    fun bindUpdateSource(source: PlayUpdateSource): UpdateSource
}
