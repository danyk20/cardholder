package io.github.danyk20.cardholder.core.domain.repository

import io.github.danyk20.cardholder.core.model.CardSort
import io.github.danyk20.cardholder.core.model.ThemeMode
import io.github.danyk20.cardholder.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setCardSort(sort: CardSort)

    suspend fun setExpiryReminders(enabled: Boolean)
}
