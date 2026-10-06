package io.github.danyk20.cardholder.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.danyk20.cardholder.core.domain.repository.UserPreferencesRepository
import io.github.danyk20.cardholder.core.model.CardSort
import io.github.danyk20.cardholder.core.model.ThemeMode
import io.github.danyk20.cardholder.core.model.UserPreferences
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal class DataStoreUserPreferencesRepository @Inject constructor(private val dataStore: DataStore<Preferences>) :
    UserPreferencesRepository {
    // An unreadable preferences file falls back to the defaults instead of crashing the app.
    override val preferences: Flow<UserPreferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }.map { prefs ->
        UserPreferences(
            themeMode = prefs[THEME_MODE]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: ThemeMode.SYSTEM,
            useDynamicColor = prefs[DYNAMIC_COLOR] ?: true,
            cardSort = prefs[CARD_SORT]?.let { name -> CardSort.entries.firstOrNull { it.name == name } }
                ?: CardSort.NAME,
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setCardSort(sort: CardSort) {
        dataStore.edit { it[CARD_SORT] = sort.name }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val CARD_SORT = stringPreferencesKey("card_sort")
    }
}
