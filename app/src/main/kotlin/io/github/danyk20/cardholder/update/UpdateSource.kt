package io.github.danyk20.cardholder.update

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Where this copy of the app gets its updates; decided by the build (see the product flavors). */
enum class UpdateChannel { GOOGLE_PLAY, GITHUB }

sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck

    /**
     * A newer version exists. [version] and [notes] are `null` when the store doesn't say: Google Play
     * only reports that there is an update.
     */
    data class Available(val version: String?, val notes: String?) : UpdateCheck

    /** E.g. no network, or the store couldn't be reached. */
    data object Failed : UpdateCheck
}

sealed interface InstallResult {
    /** Android's installer or Google Play took over; the app is restarted once the update is installed. */
    data object Started : InstallResult

    /** The user first has to allow Cardholder to install apps (Settings > Install unknown apps). */
    data object NeedsPermission : InstallResult

    data object Failed : InstallResult
}

/** How Android's installer ended after [InstallResult.Started], if it didn't install the update. */
enum class InstallOutcome {
    /** The user declined Android's confirmation. */
    CANCELLED,

    /** E.g. the file isn't signed with the app's key. */
    FAILED,
}

/** Checks for and installs updates of the app; only ever contacts the network when asked to. */
interface UpdateSource {
    val channel: UpdateChannel

    suspend fun check(): UpdateCheck

    /**
     * Installs the update the last [check] found, reporting download progress (0..1) where there is a
     * download. [activity] is the screen the update was started from.
     */
    suspend fun install(activity: Activity, onProgress: (Float) -> Unit): InstallResult

    /** Installs that were started but didn't finish. Google Play reports those in its own screen. */
    val unfinishedInstalls: Flow<InstallOutcome> get() = emptyFlow()
}
