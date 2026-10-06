package io.github.danyk20.cardholder.core.domain.validation

import io.github.danyk20.cardholder.core.model.BarcodeFormat

/** Checks that a loyalty card code can be encoded in the chosen [BarcodeFormat]. */
object BarcodeValidator {
    private const val CODE_39_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%"
    private const val CODABAR_CHARSET = "0123456789-$:/.+"
    private const val CODABAR_GUARDS = "ABCD"
    private const val MAX_LINEAR_LENGTH = 80
    private const val MAX_2D_LENGTH = 2000
    private val UPC_E_LENGTH = 7..8
    private val PRINTABLE_ASCII = ' '..'~'

    @Suppress("CyclomaticComplexMethod")
    fun validate(code: String, format: BarcodeFormat): ValidationError? {
        if (code.isEmpty()) return ValidationError.REQUIRED
        return when (format) {
            BarcodeFormat.EAN_13 -> gtin(code, payloadLength = 12)

            BarcodeFormat.EAN_8 -> gtin(code, payloadLength = 7)

            BarcodeFormat.UPC_A -> gtin(code, payloadLength = 11)

            BarcodeFormat.UPC_E -> upcE(code)

            BarcodeFormat.ITF -> {
                when {
                    !code.all(Char::isDigit) -> ValidationError.INVALID_CHARACTERS
                    code.length % 2 != 0 || code.length > MAX_LINEAR_LENGTH -> ValidationError.INVALID_LENGTH
                    else -> null
                }
            }

            BarcodeFormat.CODE_39, BarcodeFormat.CODE_93 -> charset(code.uppercase(), CODE_39_CHARSET)

            BarcodeFormat.CODABAR -> codabar(code.uppercase())

            BarcodeFormat.CODE_128 -> {
                when {
                    !code.all { it in PRINTABLE_ASCII } -> ValidationError.INVALID_CHARACTERS
                    code.length > MAX_LINEAR_LENGTH -> ValidationError.INVALID_LENGTH
                    else -> null
                }
            }

            BarcodeFormat.QR_CODE, BarcodeFormat.AZTEC, BarcodeFormat.DATA_MATRIX, BarcodeFormat.PDF_417 -> {
                if (code.length > MAX_2D_LENGTH) ValidationError.INVALID_LENGTH else null
            }
        }
    }

    /** EAN/UPC codes may be entered with or without their check digit. */
    private fun gtin(code: String, payloadLength: Int): ValidationError? = when {
        !code.all(Char::isDigit) -> ValidationError.INVALID_CHARACTERS

        code.length == payloadLength -> null

        code.length == payloadLength + 1 -> {
            if (Checksums.isGtinValid(code)) null else ValidationError.INVALID_CHECKSUM
        }

        else -> ValidationError.INVALID_LENGTH
    }

    private fun upcE(code: String): ValidationError? = when {
        !code.all(Char::isDigit) -> ValidationError.INVALID_CHARACTERS
        code.length !in UPC_E_LENGTH -> ValidationError.INVALID_LENGTH
        code.first() !in "01" -> ValidationError.INVALID_CHARACTERS
        else -> null
    }

    private fun charset(code: String, allowed: String): ValidationError? = when {
        !code.all { it in allowed } -> ValidationError.INVALID_CHARACTERS
        code.length > MAX_LINEAR_LENGTH -> ValidationError.INVALID_LENGTH
        else -> null
    }

    private fun codabar(code: String): ValidationError? {
        val hasGuards = code.length >= 2 && code.first() in CODABAR_GUARDS && code.last() in CODABAR_GUARDS
        val body = if (hasGuards) code.substring(1, code.length - 1) else code
        return when {
            body.isEmpty() -> ValidationError.REQUIRED
            else -> charset(body, CODABAR_CHARSET)
        }
    }
}
