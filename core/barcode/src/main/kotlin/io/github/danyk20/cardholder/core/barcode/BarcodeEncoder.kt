package io.github.danyk20.cardholder.core.barcode

import com.google.zxing.BarcodeFormat as ZxingFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.danyk20.cardholder.core.model.BarcodeFormat

/** Encodes loyalty card codes into module matrices with ZXing. Pure JVM, safe to call off the main thread. */
object BarcodeEncoder {
    /**
     * Encodes [content] so that the result is at least [width] × [height] pixels with whole-pixel
     * modules, which keeps the code crisp for scanners. Returns `null` if [content] cannot be
     * represented in [format].
     */
    internal fun encode(content: String, format: BarcodeFormat, width: Int, height: Int): BitMatrix? = try {
        MultiFormatWriter().encode(content, format.toZxing(), width, height, hints(format))
    } catch (_: WriterException) {
        null
    } catch (@Suppress("TooGenericExceptionCaught") _: RuntimeException) {
        // ZXing throws various runtime exceptions for content a symbology can't hold; never crash drawing.
        null
    }

    /** Whether [content] can be represented in [format]. */
    fun canEncode(content: String, format: BarcodeFormat): Boolean = encode(content, format, 1, 1) != null

    private fun hints(format: BarcodeFormat): Map<EncodeHintType, Any> = buildMap {
        put(EncodeHintType.CHARACTER_SET, Charsets.UTF_8.name())
        // Quiet zone around the symbol, in modules; scanners need it to find the code.
        put(EncodeHintType.MARGIN, if (format.isTwoDimensional) QUIET_ZONE_2D else QUIET_ZONE_1D)
        if (format == BarcodeFormat.QR_CODE) put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
    }

    private fun BarcodeFormat.toZxing(): ZxingFormat = when (this) {
        BarcodeFormat.QR_CODE -> ZxingFormat.QR_CODE
        BarcodeFormat.AZTEC -> ZxingFormat.AZTEC
        BarcodeFormat.DATA_MATRIX -> ZxingFormat.DATA_MATRIX
        BarcodeFormat.PDF_417 -> ZxingFormat.PDF_417
        BarcodeFormat.EAN_13 -> ZxingFormat.EAN_13
        BarcodeFormat.EAN_8 -> ZxingFormat.EAN_8
        BarcodeFormat.UPC_A -> ZxingFormat.UPC_A
        BarcodeFormat.UPC_E -> ZxingFormat.UPC_E
        BarcodeFormat.CODE_128 -> ZxingFormat.CODE_128
        BarcodeFormat.CODE_39 -> ZxingFormat.CODE_39
        BarcodeFormat.CODE_93 -> ZxingFormat.CODE_93
        BarcodeFormat.ITF -> ZxingFormat.ITF
        BarcodeFormat.CODABAR -> ZxingFormat.CODABAR
    }

    private const val QUIET_ZONE_2D = 2
    private const val QUIET_ZONE_1D = 10
}
