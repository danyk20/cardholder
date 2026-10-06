package io.github.danyk20.cardholder.core.scanning

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/** Starts the system document scanner (edge detection, perspective correction, cropping) for one card side. */
fun interface CardSideScanner {
    fun scan()
}

/**
 * Remembers a [CardSideScanner] backed by the ML Kit document scanner, which runs in Google Play
 * services and needs no camera permission. [onResult] receives the scanned JPEG's URI, or `null` if
 * the user cancelled; [onUnavailable] is called when the scanner can't run (e.g. no Play services).
 */
@Composable
fun rememberCardSideScanner(onResult: (uri: String?) -> Unit, onUnavailable: () -> Unit): CardSideScanner {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val uri = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.firstOrNull()?.imageUri
        currentOnResult(uri?.toString())
    }
    return remember(context, launcher) {
        val client = GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build(),
        )
        CardSideScanner {
            val activity = context.findActivity() ?: return@CardSideScanner currentOnUnavailable()
            client.getStartScanIntent(activity)
                .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener { currentOnUnavailable() }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
