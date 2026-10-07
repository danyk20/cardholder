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
    private const val USUAL_LENGTH = 16

    /** Removes the spaces and dashes users type to group digits. */
    fun normalizeNumber(number: String): String = number.filterNot { it in SEPARATORS }

    /**
     * Live check while the number is typed: [InputStatus.VALID] as soon as it has a length the network
     * issues and a matching check digit (Luhn), [InputStatus.INVALID] once it can't become valid any
     * more, otherwise [InputStatus.INCOMPLETE].
     */
    fun numberStatus(number: String): InputStatus {
        val digits = normalizeNumber(number)
        if (digits.isEmpty()) return InputStatus.INCOMPLETE
        if (!digits.all(Char::isDigit)) return InputStatus.INVALID
        val lengths = CardNetwork.detect(digits).lengths
        // Most cards have 16 digits; a mistake is reported from there on (15 for American Express).
        val usualLength = if (USUAL_LENGTH in lengths) USUAL_LENGTH else lengths.max()
        return when {
            digits.length in lengths && Checksums.isLuhnValid(digits) -> InputStatus.VALID
            digits.length >= usualLength -> InputStatus.INVALID
            else -> InputStatus.INCOMPLETE
        }
    }

    /** Live check of an expiry: invalid once a whole date was typed that isn't a real month. */
    fun expiryStatus(input: String): InputStatus = when {
        parseExpiry(input) != null -> InputStatus.VALID
        input.count(Char::isDigit) >= EXPIRY_PATTERN_LENGTH -> InputStatus.INVALID
        else -> InputStatus.INCOMPLETE
    }

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
