package io.github.danyk20.cardholder.core.domain.model

import io.github.danyk20.cardholder.core.model.CardType

/** When before its expiry a card is reminded of, and which cards get the reminder. */
enum class ExpiryReminder(val monthsBefore: Long, val types: Set<CardType>) {
    /** A month before: time to get the replacement. */
    FINAL(monthsBefore = 1, types = setOf(CardType.BANK, CardType.ID)),

    /**
     * Seven months before, for IDs only: many countries require six months of validity left for
     * travel, so this leaves a month to renew the document before it stops being usable abroad.
     */
    TRAVEL(monthsBefore = 7, types = setOf(CardType.ID)),
}
