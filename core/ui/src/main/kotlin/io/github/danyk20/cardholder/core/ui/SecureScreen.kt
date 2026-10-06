package io.github.danyk20.cardholder.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy

/**
 * Prevents screenshots, screen recording and the recents thumbnail from showing this screen
 * while it is in the composition.
 */
@Composable
fun SecureScreen() {
    val window = LocalContext.current.findActivity()?.window ?: return
    DisposableEffect(window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

internal fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Dialogs are separate windows and don't inherit [SecureScreen]'s flag; use these properties for
 * every dialog shown from a screen with sensitive data.
 */
fun secureDialogProperties(usePlatformDefaultWidth: Boolean = true): DialogProperties =
    DialogProperties(usePlatformDefaultWidth = usePlatformDefaultWidth, securePolicy = SecureFlagPolicy.SecureOn)
