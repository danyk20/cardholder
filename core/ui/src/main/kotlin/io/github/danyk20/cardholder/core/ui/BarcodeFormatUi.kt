package io.github.danyk20.cardholder.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.danyk20.cardholder.core.model.BarcodeFormat

/** Name of a symbology as printed in specifications and recognized by users. */
@Composable
fun barcodeFormatLabel(format: BarcodeFormat): String = when (format) {
    BarcodeFormat.QR_CODE -> stringResource(R.string.format_qr_code)
    BarcodeFormat.AZTEC -> "Aztec"
    BarcodeFormat.DATA_MATRIX -> "Data Matrix"
    BarcodeFormat.PDF_417 -> "PDF417"
    BarcodeFormat.EAN_13 -> "EAN-13"
    BarcodeFormat.EAN_8 -> "EAN-8"
    BarcodeFormat.UPC_A -> "UPC-A"
    BarcodeFormat.UPC_E -> "UPC-E"
    BarcodeFormat.CODE_128 -> "Code 128"
    BarcodeFormat.CODE_39 -> "Code 39"
    BarcodeFormat.CODE_93 -> "Code 93"
    BarcodeFormat.ITF -> "ITF"
    BarcodeFormat.CODABAR -> "Codabar"
}
