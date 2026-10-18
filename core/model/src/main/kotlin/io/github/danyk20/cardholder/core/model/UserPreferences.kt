package io.github.danyk20.cardholder.core.model

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val cardSort: CardSort = CardSort.NAME,
    /** Notify a month before a bank card or ID expires. */
    val expiryReminders: Boolean = true,
    /** Whether and when cards are suggested by where they were used before. */
    val placeSuggestions: PlaceSuggestions = PlaceSuggestions.OFF,
)
