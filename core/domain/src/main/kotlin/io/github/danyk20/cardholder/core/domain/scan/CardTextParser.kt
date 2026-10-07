package io.github.danyk20.cardholder.core.domain.scan

import io.github.danyk20.cardholder.core.domain.validation.Checksums
import io.github.danyk20.cardholder.core.model.CardNetwork
import java.text.Normalizer
import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** What could be read from photos of a bank card. Every field is optional: OCR is best effort. */
data class BankCardText(val number: String?, val expiry: YearMonth?, val holder: String?, val cvv: String?) {
    override fun toString(): String = "BankCardText(██)"
}

/** What could be read from the machine-readable zone (MRZ) of an ID card or passport. */
data class IdDocumentText(
    val documentNumber: String?,
    val expiry: LocalDate?,
    /** ISO 3166-1 alpha-2 code of the issuing country. */
    val issuingCountry: String?,
    /** "Given names Surname" as printed in the MRZ. */
    val holderName: String?,
) {
    override fun toString(): String = "IdDocumentText(██)"
}

/**
 * Turns text recognised on card photos into form values. Only plausible values are returned: card
 * numbers must pass the Luhn check and MRZ fields their check digits, so a misread is dropped rather
 * than filled in.
 */
@Suppress("TooManyFunctions")
object CardTextParser {
    private val NUMBER = Regex("""(?<!\d)(?:\d[ \-]?){11,18}\d(?!\d)""")
    private val EXPIRY = Regex("""(?<!\d)(0[1-9]|1[0-2])\s?[/\-.]\s?(\d{4}|\d{2})(?!\d)""")
    private val SHORT_NUMBER = Regex("""(?<!\d)\d{3,4}(?!\d)""")
    private val SIGNATURE_STRIP = Regex("""(?<!\d)\d{4}\s+(\d{3})(?!\d)""")
    private val CVV_LABEL = Regex("""\b(CVV2?|CVC2?|CID|CSC|SECURITY CODE)\b""", RegexOption.IGNORE_CASE)
    private val HOLDER = Regex("""^[\p{Lu}][\p{Lu}.'\- ]{2,30}[\p{Lu}.]$""")

    /** Printed on cards but never part of a holder name. */
    private val NOT_A_NAME = setOf(
        "VISA", "MASTERCARD", "MAESTRO", "DEBIT", "CREDIT", "CARD", "BANK", "VALID", "THRU", "FROM", "UNTIL",
        "MONTH", "YEAR", "GOOD", "ELECTRONIC", "PLATINUM", "GOLD", "CLASSIC", "BUSINESS", "WORLD", "PREMIER",
        "CONTACTLESS", "EXPIRES", "EXPIRY", "MEMBER", "SINCE", "AMERICAN", "EXPRESS", "UNIONPAY", "DISCOVER",
        "JCB", "DINERS", "CLUB", "INTERNATIONAL", "PREPAID", "SIGNATURE", "AUTHORIZED", "CUSTOMER", "SERVICE",
        "INFINITE", "STANDARD", "CORPORATE", "VIRTUAL", "PAY",
    )

    private const val CENTURY = 2000
    private const val SHORT_YEAR_LENGTH = 2
    private const val MIN_NAME_WORDS = 2
    private const val MAX_NAME_WORDS = 4
    private const val LAST_DIGITS = 4
    private const val MIN_BRAND_LENGTH = 3

    /** Reads a bank card from the text of its [front] and [back]; [bankNames] are ignored as holder names. */
    fun parseBank(front: List<String>, back: List<String>, bankNames: Collection<String> = emptyList()): BankCardText {
        val lines = front + back
        val number = cardNumber(lines)
        return BankCardText(
            number = number,
            expiry = expiry(lines),
            holder = holder(lines, bankNames),
            cvv = cvv(back, number),
        )
    }

    private fun cardNumber(lines: List<String>): String? = (lines + lines.joinToString(" "))
        .asSequence()
        .flatMap { line -> NUMBER.findAll(line).map { match -> match.value.filter(Char::isDigit) } }
        .filter { it.length in CardNetwork.detect(it).lengths && Checksums.isLuhnValid(it) }
        .maxByOrNull { if (CardNetwork.detect(it) == CardNetwork.UNKNOWN) 0 else 1 }

    /** Cards print "valid from" and "valid thru"; the later date is the expiry. */
    private fun expiry(lines: List<String>): YearMonth? = lines
        .flatMap { line -> EXPIRY.findAll(line).toList() }
        .mapNotNull { match ->
            val month = match.groupValues[1].toInt()
            val year = match.groupValues[2].let {
                if (it.length ==
                    SHORT_YEAR_LENGTH
                ) {
                    CENTURY + it.toInt()
                } else {
                    it.toInt()
                }
            }
            runCatching { YearMonth.of(year, month) }.getOrNull()
        }
        .maxOrNull()

    private fun holder(lines: List<String>, bankNames: Collection<String>): String? {
        val banks = bankNames.map { it.uppercase(Locale.ROOT) }
        return lines.map { it.trim().replace(Regex("\\s+"), " ") }.firstOrNull { line ->
            val words = line.split(' ')
            HOLDER.matches(line) &&
                words.size in MIN_NAME_WORDS..MAX_NAME_WORDS &&
                words.none { it.trim('.') in NOT_A_NAME } &&
                banks.none { line.contains(it) }
        }
    }

    /**
     * The CVV is only taken when it is unambiguous: next to a "CVV"/"CVC" label, or as the three digits
     * after the last four digits of the number in the signature strip.
     */
    private fun cvv(back: List<String>, number: String?): String? {
        val length = number?.let { CardNetwork.detect(it).cvvLength }
        val labelled = back.filter { CVV_LABEL.containsMatchIn(it) }
            .flatMap { line -> SHORT_NUMBER.findAll(line).map { it.value }.toList() }
        // Without the card number, "1234 567" could just as well be part of a phone number.
        val inStrip = if (number == null) {
            emptyList()
        } else {
            back.flatMap { line ->
                SIGNATURE_STRIP.findAll(line)
                    .filter { it.value.startsWith(number.takeLast(LAST_DIGITS)) }
                    .map { it.groupValues[1] }
                    .toList()
            }
        }
        val candidates = (labelled + inStrip).distinct().filter { length == null || it.length == length }
        return candidates.singleOrNull()
    }

    /** Parses an ICAO 9303 machine-readable zone (ID cards: 3 lines of 30, passports: 2 of 44 or 36). */
    fun parseMrz(lines: List<String>): IdDocumentText? {
        val mrz = lines.map(::normalizeMrzLine).filter { it.count { c -> c == '<' } >= 2 }
        return td1(mrz) ?: twoLine(mrz, length = 44) ?: twoLine(mrz, length = 36)
    }

    private fun normalizeMrzLine(line: String): String =
        line.uppercase(Locale.ROOT).replace('«', '<').replace(" ", "").filter { it.isLetterOrDigit() || it == '<' }

    @Suppress("MagicNumber") // Field positions are defined by ICAO 9303.
    private fun td1(lines: List<String>): IdDocumentText? {
        val index = lines.indices.firstOrNull { i ->
            i + 2 < lines.size && lines.subList(i, i + 3).all { it.length == 30 }
        } ?: return null
        val (first, second, third) = lines.subList(index, index + 3)
        // Long document numbers continue in the optional field when the check digit is '<'.
        val documentNumber = if (first[14] == '<') {
            val overflow = first.substring(15).substringBefore('<')
            (first.substring(5, 14) + overflow.dropLast(1)).takeIf { checkDigit(it) == overflow.lastOrNull() }
        } else {
            first.substring(5, 14).takeIf { checkDigit(it) == first[14] }
        }
        return IdDocumentText(
            documentNumber = documentNumber?.trimEnd('<'),
            expiry = mrzDate(second.substring(8, 14), second[14]),
            issuingCountry = alpha2(first.substring(2, 5)),
            holderName = mrzName(third),
        )
    }

    @Suppress("MagicNumber") // Field positions are defined by ICAO 9303.
    private fun twoLine(lines: List<String>, length: Int): IdDocumentText? {
        val index = lines.indices.firstOrNull { i ->
            i + 1 < lines.size && lines[i].length == length && lines[i + 1].length == length
        } ?: return null
        val (first, second) = lines.subList(index, index + 2)
        return IdDocumentText(
            documentNumber = second.substring(0, 9).takeIf { checkDigit(it) == second[9] }?.trimEnd('<'),
            expiry = mrzDate(second.substring(21, 27), second[27]),
            issuingCountry = alpha2(first.substring(2, 5)),
            holderName = mrzName(first.substring(5)),
        )
    }

    /** `YYMMDD` with its check digit; MRZ expiry years are always in this century. */
    @Suppress("MagicNumber")
    private fun mrzDate(field: String, check: Char): LocalDate? {
        val digits = field.map(::digitLookalike)
        if (checkDigit(digits.joinToString("")) != digitLookalike(check)) return null
        val text = digits.joinToString("")
        if (!text.all(Char::isDigit)) return null
        return try {
            LocalDate.of(CENTURY + text.take(2).toInt(), text.substring(2, 4).toInt(), text.substring(4, 6).toInt())
        } catch (_: DateTimeException) {
            null
        }
    }

    /** OCR often reads digits in numeric MRZ fields as similar letters. */
    private fun digitLookalike(c: Char): Char = when (c) {
        'O', 'Q', 'D' -> '0'
        'I', 'L' -> '1'
        'Z' -> '2'
        'S' -> '5'
        'B' -> '8'
        else -> c
    }

    private fun mrzName(field: String): String? {
        val surname = field.substringBefore("<<").replace('<', ' ').trim()
        val given = field.substringAfter("<<", "").replace('<', ' ').trim().replace(Regex("\\s+"), " ")
        val name = listOf(given, surname).filter { it.isNotEmpty() }.joinToString(" ")
        return name.takeIf { it.isNotEmpty() && it.all { c -> c.isLetter() || c == ' ' } }
            ?.split(' ')
            ?.joinToString(" ") { word -> word.lowercase(Locale.ROOT).replaceFirstChar(Char::uppercaseChar) }
    }

    /** ICAO 9303 check digit (weights 7, 3, 1; `<` = 0, A = 10 … Z = 35). */
    @Suppress("MagicNumber")
    internal fun checkDigit(field: String): Char {
        val weights = intArrayOf(7, 3, 1)
        val sum = field.mapIndexed { i, c ->
            val value = when (c) {
                in '0'..'9' -> c - '0'
                in 'A'..'Z' -> c - 'A' + 10
                else -> 0
            }
            value * weights[i % 3]
        }.sum()
        return '0' + sum % 10
    }

    private fun alpha2(alpha3: String): String? {
        val code = alpha3.trimEnd('<')
        // Germany uses "D" instead of its ISO code.
        if (code == "D") return "DE"
        return Locale.getISOCountries().firstOrNull { Locale("", it).isO3Country == code }
    }

    /**
     * Finds which of [names] (shops or banks) is printed on the card: the longest name that appears as a
     * whole word. Names shorter than three letters are ignored, they match too easily.
     */
    fun findBrand(lines: List<String>, names: Collection<String>): String? {
        val text = " ${lines.joinToString(" ") { simplify(it) }} "
        return names.filter { it.length >= MIN_BRAND_LENGTH }
            .sortedByDescending { it.length }
            .firstOrNull { name ->
                Regex("""(?<![\p{L}\d])${Regex.escape(simplify(name))}(?![\p{L}\d])""").containsMatchIn(text)
            }
    }

    /** Case and accents differ between logos and catalogue names. */
    private fun simplify(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").uppercase(Locale.ROOT)
}
