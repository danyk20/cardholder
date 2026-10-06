package io.github.danyk20.cardholder.core.domain.repository

import io.github.danyk20.cardholder.core.domain.model.SecureResult
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.ImageRef

interface CardImageRepository {
    /** Returns the decrypted, encoded (JPEG) image. Images of locked cards require prior authentication. */
    suspend fun read(ref: ImageRef): SecureResult<ByteArray>
}

/** A barcode found in an image or camera frame. */
data class ScannedBarcode(val code: String, val format: BarcodeFormat) {
    override fun toString(): String = "ScannedBarcode(format=$format)"
}

/** Finds loyalty card barcodes in photos. */
interface BarcodeImageScanner {
    /** Returns the most prominent supported barcode in the image at [uri], or `null` if there is none. */
    suspend fun scan(uri: String): ScannedBarcode?
}
