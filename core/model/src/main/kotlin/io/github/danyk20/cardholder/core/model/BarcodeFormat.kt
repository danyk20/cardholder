package io.github.danyk20.cardholder.core.model

/** Symbologies supported for loyalty cards, both for scanning and rendering. */
enum class BarcodeFormat(val isTwoDimensional: Boolean) {
    QR_CODE(isTwoDimensional = true),
    AZTEC(isTwoDimensional = true),
    DATA_MATRIX(isTwoDimensional = true),
    PDF_417(isTwoDimensional = true),
    EAN_13(isTwoDimensional = false),
    EAN_8(isTwoDimensional = false),
    UPC_A(isTwoDimensional = false),
    UPC_E(isTwoDimensional = false),
    CODE_128(isTwoDimensional = false),
    CODE_39(isTwoDimensional = false),
    CODE_93(isTwoDimensional = false),
    ITF(isTwoDimensional = false),
    CODABAR(isTwoDimensional = false),
    ;

    /** Square symbols (QR, Aztec, Data Matrix) as opposed to wide ones (linear codes, PDF417). */
    val isSquare: Boolean get() = isTwoDimensional && this != PDF_417
}
