package io.github.danyk20.cardholder.core.model

/** How the card list is ordered. */
enum class CardSort {
    /** Alphabetically by title. */
    NAME,

    /** Most recently added first. */
    DATE_ADDED,

    /** The cards used most often first. */
    MOST_USED,

    /** The order the user arranged. */
    CUSTOM,
}
