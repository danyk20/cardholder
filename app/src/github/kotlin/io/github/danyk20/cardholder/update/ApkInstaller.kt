package io.github.danyk20.cardholder.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.content.IntentCompat
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Hands a downloaded APK to Android's package installer. Android shows its own confirmation, and only
 * accepts the APK as an update if it's signed with the same key as the installed app.
 */
internal object ApkInstaller {
    /** Reported by [InstallStatusReceiver] for installs that didn't finish. */
    val unfinished = MutableSharedFlow<InstallOutcome>(extraBufferCapacity = 1)

    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { output ->
                apk.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            val status = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallStatusReceiver::class.java),
                // Mutable so the installer can add the status; explicit, so only this app receives it.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(status.intentSender)
        }
    }
}

/** Shows Android's install confirmation, and logs installs that failed. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let { confirm ->
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }

            // The app is restarted with the new version; nothing to do.
            PackageInstaller.STATUS_SUCCESS -> Unit

            PackageInstaller.STATUS_FAILURE_ABORTED -> ApkInstaller.unfinished.tryEmit(InstallOutcome.CANCELLED)

            else -> {
                Log.w(
                    TAG,
                    "Update not installed: $status ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}",
                )
                ApkInstaller.unfinished.tryEmit(InstallOutcome.FAILED)
            }
        }
    }

    private companion object {
        const val TAG = "Updates"
    }
}
