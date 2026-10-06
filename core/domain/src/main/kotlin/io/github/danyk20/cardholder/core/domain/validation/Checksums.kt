@file:Suppress("MagicNumber") // Check digit algorithms are defined in terms of these constants.

package io.github.danyk20.cardholder.core.domain.validation

internal object Checksums {
    /** Luhn (mod 10) check used by payment card numbers. [digits] must contain only digits. */
    fun isLuhnValid(digits: String): Boolean {
        if (digits.isEmpty()) return false
        val sum = digits.reversed().mapIndexed { index, char ->
            val digit = char.digitToInt()
            if (index % 2 == 1) (digit * 2).let { if (it > 9) it - 9 else it } else digit
        }.sum()
        return sum % 10 == 0
    }

    /**
     * Validates the GS1 check digit of an EAN-8, EAN-13 or UPC-A number (last digit is the check digit).
     */
    fun isGtinValid(digits: String): Boolean {
        if (digits.length < 2) return false
        return gtinCheckDigit(digits.dropLast(1)) == digits.last().digitToInt()
    }

    /** Computes the GS1 check digit for [payload] (all digits except the check digit). */
    fun gtinCheckDigit(payload: String): Int {
        val sum = payload.reversed().mapIndexed { index, char ->
            char.digitToInt() * if (index % 2 == 0) 3 else 1
        }.sum()
        return (10 - sum % 10) % 10
    }
}
