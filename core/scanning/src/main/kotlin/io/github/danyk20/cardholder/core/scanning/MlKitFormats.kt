package io.github.danyk20.cardholder.core.scanning

import com.google.mlkit.vision.barcode.common.Barcode as MlKitBarcode
import io.github.danyk20.cardholder.core.domain.repository.ScannedBarcode
import io.github.danyk20.cardholder.core.model.BarcodeFormat

/** Maps ML Kit format constants to [BarcodeFormat]; `null` for formats loyalty cards don't use. */
internal fun mlKitFormatToModel(format: Int): BarcodeFormat? = when (format) {
    MlKitBarcode.FORMAT_QR_CODE -> BarcodeFormat.QR_CODE
    MlKitBarcode.FORMAT_AZTEC -> BarcodeFormat.AZTEC
    MlKitBarcode.FORMAT_DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
    MlKitBarcode.FORMAT_PDF417 -> BarcodeFormat.PDF_417
    MlKitBarcode.FORMAT_EAN_13 -> BarcodeFormat.EAN_13
    MlKitBarcode.FORMAT_EAN_8 -> BarcodeFormat.EAN_8
    MlKitBarcode.FORMAT_UPC_A -> BarcodeFormat.UPC_A
    MlKitBarcode.FORMAT_UPC_E -> BarcodeFormat.UPC_E
    MlKitBarcode.FORMAT_CODE_128 -> BarcodeFormat.CODE_128
    MlKitBarcode.FORMAT_CODE_39 -> BarcodeFormat.CODE_39
    MlKitBarcode.FORMAT_CODE_93 -> BarcodeFormat.CODE_93
    MlKitBarcode.FORMAT_ITF -> BarcodeFormat.ITF
    MlKitBarcode.FORMAT_CODABAR -> BarcodeFormat.CODABAR
    else -> null
}

internal fun MlKitBarcode.toScanned(): ScannedBarcode? {
    val value = rawValue?.takeIf { it.isNotBlank() } ?: return null
    val format = mlKitFormatToModel(format) ?: return null
    return ScannedBarcode(value, format)
}

/** The largest barcode is most likely the card's own code rather than e.g. a small marketing QR code. */
internal fun List<MlKitBarcode>.mostProminent(): ScannedBarcode? =
    sortedByDescending { barcode -> barcode.boundingBox?.let { it.width() * it.height() } ?: 0 }
        .firstNotNullOfOrNull { it.toScanned() }
