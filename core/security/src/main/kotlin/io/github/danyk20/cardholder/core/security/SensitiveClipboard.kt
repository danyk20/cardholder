package io.github.danyk20.cardholder.core.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.danyk20.cardholder.core.domain.di.ApplicationScope
import io.github.danyk20.cardholder.core.domain.security.SecureClipboard
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Copies card data to the clipboard marked as sensitive (hidden from clipboard previews and
 * keyboard suggestions) and clears it again after a minute unless something else was copied.
 */
@Singleton
internal class SensitiveClipboard @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) : SecureClipboard {
    private var clearJob: Job? = null

    override fun copy(label: String, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(label, text).apply {
            description.extras = PersistableBundle().apply {
                val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ClipDescription.EXTRA_IS_SENSITIVE
                } else {
                    "android.content.extra.IS_SENSITIVE"
                }
                putBoolean(key, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(CLEAR_AFTER)
            val current = clipboard.primaryClip
            if (current != null && current.itemCount > 0 && current.getItemAt(0).text?.toString() == text) {
                clipboard.clearPrimaryClip()
            }
        }
    }

    private companion object {
        val CLEAR_AFTER: Duration = 60.seconds
    }
}
