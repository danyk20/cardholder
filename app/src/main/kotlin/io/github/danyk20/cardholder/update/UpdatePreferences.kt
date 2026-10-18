package io.github.danyk20.cardholder.update

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the app may check for updates by itself (off by default), and when it last checked. */
@Singleton
class UpdatePreferences @Inject constructor(@ApplicationContext context: Context, private val clock: Clock) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var autoCheck: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECK, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_CHECK, value) }

    val lastCheck: Instant?
        get() = prefs.getLong(KEY_LAST_CHECK, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    fun recordCheck() = prefs.edit { putLong(KEY_LAST_CHECK, clock.millis()) }

    /** An automatic check is due: switched on, and the last check (of any kind) is a week old. */
    fun isAutoCheckDue(): Boolean =
        autoCheck && lastCheck.let { it == null || Duration.between(it, clock.instant()) >= AUTO_CHECK_INTERVAL }

    private companion object {
        const val PREFS = "updates"
        const val KEY_AUTO_CHECK = "auto_check"
        const val KEY_LAST_CHECK = "last_check"
        val AUTO_CHECK_INTERVAL: Duration = Duration.ofDays(7)
    }
}
