package io.github.danyk20.cardholder.core.scanning

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import io.github.danyk20.cardholder.core.domain.repository.ScannedBarcode
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/** Runs ML Kit on camera frames and reports the first supported barcode exactly once. */
internal class BarcodeAnalyzer(private val onDetected: (ScannedBarcode) -> Unit) :
    ImageAnalysis.Analyzer,
    Closeable {
    private val scanner = BarcodeScanning.getClient()
    private val detected = AtomicBoolean(false)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || detected.get()) {
            imageProxy.close()
            return
        }
        // Runs on the analysis thread, where an exception would kill the app; e.g. a frame arriving
        // after the scanner was closed. Such a frame is simply skipped.
        try {
            scanner.process(InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees))
                .addOnSuccessListener { barcodes ->
                    val barcode = barcodes.mostProminent()
                    if (barcode != null && detected.compareAndSet(false, true)) onDetected(barcode)
                }
                .addOnCompleteListener { imageProxy.close() }
        } catch (@Suppress("TooGenericExceptionCaught") _: RuntimeException) {
            imageProxy.close()
        }
    }

    override fun close() = scanner.close()
}
