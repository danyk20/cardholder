package io.github.danyk20.cardholder.core.model

/** How the card list is ordered. */
enum class CardSort {
    /** Alphabetically by title. */
    NAME,

    /** Most recently added first. */
    DATE_ADDED,

    /** The order the user arranged. */
    CUSTOM,
}
