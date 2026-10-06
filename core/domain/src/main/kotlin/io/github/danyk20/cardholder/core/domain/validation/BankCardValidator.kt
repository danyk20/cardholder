package io.github.danyk20.cardholder.core.domain.validation

import io.github.danyk20.cardholder.core.model.CardNetwork
import java.time.DateTimeException
import java.time.YearMonth

/** Validation rules for payment card fields. */
object BankCardValidator {
    private val NUMBER_LENGTH = 12..19
    private val SEPARATORS = setOf(' ', '-')
    private val CVV_LENGTH = 3..4
    private const val EXPIRY_PATTERN_LENGTH = 4
    private const val EXPIRY_LONG_PATTERN_LENGTH = 6
    private const val YEAR_LENGTH = 4
    private const val CENTURY = 2000

    /** Removes the spaces and dashes users type to group digits. */
    fun normalizeNumber(number: String): String = number.filterNot { it in SEPARATORS }

    fun validateNumber(number: String): ValidationError? {
        val digits = normalizeNumber(number)
        return when {
            digits.isEmpty() -> ValidationError.REQUIRED
            !digits.all(Char::isDigit) -> ValidationError.INVALID_CHARACTERS
            digits.length !in NUMBER_LENGTH -> ValidationError.INVALID_LENGTH
            !Checksums.isLuhnValid(digits) -> ValidationError.INVALID_CHECKSUM
            else -> null
        }
    }

    /** Parses an expiry typed as `MMYY`, `MM/YY` or `MM/YYYY`; returns `null` if it is not a valid month. */
    fun parseExpiry(input: String): YearMonth? {
        val digits = input.filter(Char::isDigit)
        val (month, year) = when (digits.length) {
            EXPIRY_PATTERN_LENGTH -> digits.take(2).toInt() to CENTURY + digits.takeLast(2).toInt()
            EXPIRY_LONG_PATTERN_LENGTH -> digits.take(2).toInt() to digits.takeLast(YEAR_LENGTH).toInt()
            else -> return null
        }
        return try {
            YearMonth.of(year, month)
        } catch (_: DateTimeException) {
            null
        }
    }

    fun validateExpiry(input: String): ValidationError? = when {
        input.isBlank() -> ValidationError.REQUIRED
        parseExpiry(input) == null -> ValidationError.INVALID_DATE
        else -> null
    }

    fun isExpired(expiry: YearMonth, today: YearMonth): Boolean = expiry < today

    fun validateHolder(holder: String): ValidationError? = if (holder.isBlank()) ValidationError.REQUIRED else null

    /** An empty CVV is valid – storing it is optional. */
    fun validateCvv(cvv: String, network: CardNetwork): ValidationError? = when {
        cvv.isEmpty() -> null

        !cvv.all(Char::isDigit) -> ValidationError.INVALID_CHARACTERS

        cvv.length != network.cvvLength && !(network == CardNetwork.UNKNOWN && cvv.length in CVV_LENGTH) -> {
            ValidationError.INVALID_LENGTH
        }

        else -> null
    }
}
