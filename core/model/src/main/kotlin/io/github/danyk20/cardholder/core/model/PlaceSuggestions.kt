package io.github.danyk20.cardholder.core.model

/** When the app suggests cards based on where they were used before. */
enum class PlaceSuggestions {
    /** No location or Wi‑Fi is ever read. */
    OFF,

    /** Suggestions on the card list, read only while the app is open. */
    IN_APP,

    /** Also a notification when arriving at a known place (needs background location). */
    ARRIVAL,
}
