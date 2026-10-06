package io.github.danyk20.cardholder.core.ui

import io.github.danyk20.cardholder.core.model.CardNetwork
import java.time.YearMonth
import java.util.Locale

private const val GROUP_SIZE = 4
private const val CENTURY = 100
private val AMEX_GROUPS = listOf(4, 6, 5)

/** Formats a card number the way it is printed: 4-6-5 for American Express, blocks of four otherwise. */
fun formatCardNumber(number: String, network: CardNetwork = CardNetwork.detect(number)): String {
    val groups = if (network == CardNetwork.AMERICAN_EXPRESS) AMEX_GROUPS else emptyList()
    val parts = mutableListOf<String>()
    var index = 0
    for (size in groups) {
        if (index >= number.length) break
        parts += number.substring(index, minOf(index + size, number.length))
        index += size
    }
    if (index < number.length) parts += number.substring(index).chunked(GROUP_SIZE)
    return parts.joinToString(" ")
}

fun formatExpiry(expiry: YearMonth): String =
    String.format(Locale.ROOT, "%02d/%02d", expiry.monthValue, expiry.year % CENTURY)
