package io.github.danyk20.cardholder.core.scanning

import android.content.Context
import androidx.core.net.toUri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.danyk20.cardholder.core.domain.di.IoDispatcher
import io.github.danyk20.cardholder.core.domain.repository.BarcodeImageScanner
import io.github.danyk20.cardholder.core.domain.repository.ScannedBarcode
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Finds barcodes in card photos with the bundled (offline) ML Kit model. */
internal class MlKitBarcodeImageScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BarcodeImageScanner {
    override suspend fun scan(uri: String): ScannedBarcode? {
        val image = withContext(ioDispatcher) {
            try {
                InputImage.fromFilePath(context, uri.toUri())
            } catch (_: IOException) {
                null
            }
        } ?: return null
        val scanner = BarcodeScanning.getClient()
        return try {
            scanner.process(image).awaitOrNull()?.mostProminent()
        } finally {
            scanner.close()
        }
    }
}

/** Awaits a Play Services task, returning `null` on failure or cancellation. */
internal suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        continuation.resume(if (task.isSuccessful) task.result else null)
    }
}
