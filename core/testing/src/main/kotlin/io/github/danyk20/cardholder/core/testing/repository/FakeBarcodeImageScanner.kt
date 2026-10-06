package io.github.danyk20.cardholder.core.testing.repository

import io.github.danyk20.cardholder.core.domain.repository.BarcodeImageScanner
import io.github.danyk20.cardholder.core.domain.repository.ScannedBarcode

/** Returns the barcode registered for a URI in [barcodes]. */
class FakeBarcodeImageScanner(val barcodes: MutableMap<String, ScannedBarcode> = mutableMapOf()) : BarcodeImageScanner {
    override suspend fun scan(uri: String): ScannedBarcode? = barcodes[uri]
}
