package io.github.danyk20.cardholder.core.model

/**
 * Payment card network, derived from the issuer identification number (IIN) prefix. [lengths] are the
 * card number lengths the network issues.
 */
@Suppress("MagicNumber")
enum class CardNetwork(val displayName: String, val lengths: Set<Int>, val cvvLength: Int = 3) {
    // 13-digit Visa numbers are no longer issued.
    VISA("Visa", setOf(16, 19)),
    MASTERCARD("Mastercard", setOf(16)),
    AMERICAN_EXPRESS("American Express", setOf(15), cvvLength = 4),
    DISCOVER("Discover", (16..19).toSet()),
    DINERS_CLUB("Diners Club", (14..19).toSet()),
    JCB("JCB", (16..19).toSet()),
    UNIONPAY("UnionPay", (16..19).toSet()),
    MAESTRO("Maestro", (12..19).toSet()),
    UNKNOWN("Card", (12..19).toSet()),
    ;

    companion object {
        /** Detects the network from the leading digits of [number]; non-digits are ignored. */
        @Suppress("CyclomaticComplexMethod", "MagicNumber")
        fun detect(number: String): CardNetwork {
            val digits = number.filter(Char::isDigit)

            fun prefix(length: Int): Int? = digits.take(length).takeIf { it.length == length }?.toInt()
            val p2 = prefix(2)
            val p3 = prefix(3)
            val p4 = prefix(4)
            return when {
                digits.startsWith("4") -> VISA
                p2 == 34 || p2 == 37 -> AMERICAN_EXPRESS
                p2 in 51..55 || p4 in 2221..2720 -> MASTERCARD
                p4 == 6011 || p2 == 65 || p3 in 644..649 -> DISCOVER
                p2 == 62 -> UNIONPAY
                p4 in 3528..3589 -> JCB
                p2 == 36 || p2 == 38 || p2 == 39 || p3 in 300..305 -> DINERS_CLUB
                p2 == 50 || p2 in 56..58 || p2 == 63 || p2 == 67 || p2 == 68 || p2 == 69 -> MAESTRO
                else -> UNKNOWN
            }
        }
    }
}
