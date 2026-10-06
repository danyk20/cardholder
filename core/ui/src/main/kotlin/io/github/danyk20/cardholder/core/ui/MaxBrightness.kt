package io.github.danyk20.cardholder.core.ui

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/** Sets the screen to full brightness and keeps it on while in the composition, e.g. for barcode scanning. */
@Composable
fun MaxBrightness() {
    val window = LocalContext.current.findActivity()?.window ?: return
    DisposableEffect(window) {
        val previous = window.attributes.screenBrightness
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = previous }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
