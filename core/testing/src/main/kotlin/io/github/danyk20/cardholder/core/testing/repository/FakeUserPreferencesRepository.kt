package io.github.danyk20.cardholder.core.testing.repository

import io.github.danyk20.cardholder.core.domain.repository.UserPreferencesRepository
import io.github.danyk20.cardholder.core.model.CardSort
import io.github.danyk20.cardholder.core.model.ThemeMode
import io.github.danyk20.cardholder.core.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeUserPreferencesRepository : UserPreferencesRepository {
    override val preferences = MutableStateFlow(UserPreferences())

    override suspend fun setThemeMode(mode: ThemeMode) = preferences.update { it.copy(themeMode = mode) }

    override suspend fun setDynamicColor(enabled: Boolean) = preferences.update { it.copy(useDynamicColor = enabled) }

    override suspend fun setCardSort(sort: CardSort) = preferences.update { it.copy(cardSort = sort) }

    override suspend fun setExpiryReminders(enabled: Boolean) = preferences.update {
        it.copy(expiryReminders = enabled)
    }
}
