package io.github.danyk20.cardholder.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.Preferences.Key
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.danyk20.cardholder.core.data.di.CountryStateStore
import io.github.danyk20.cardholder.core.domain.country.CountrySource
import io.github.danyk20.cardholder.core.domain.country.CountryState
import io.github.danyk20.cardholder.core.domain.country.CurrentCountry
import io.github.danyk20.cardholder.core.domain.country.StoredCountry
import io.github.danyk20.cardholder.core.domain.country.toState
import io.github.danyk20.cardholder.core.domain.repository.CurrentCountryRepository
import io.github.danyk20.cardholder.core.domain.usecase.ResolveCurrentCountryUseCase
import io.github.danyk20.cardholder.core.model.CountryCode
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps the detected country and the user's choice in their own DataStore. This is device state, so
 * like places it is never part of a backup.
 */
@Singleton
internal class DataStoreCurrentCountryRepository @Inject constructor(
    @CountryStateStore private val dataStore: DataStore<Preferences>,
    private val resolve: ResolveCurrentCountryUseCase,
) : CurrentCountryRepository {
    // Refreshes and choices are read-modify-write, so they run one at a time.
    private val writeMutex = Mutex()

    // An unreadable file falls back to nothing detected, instead of crashing the app.
    override val state: Flow<CountryState> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }.map { it.toStored().toState() }.distinctUntilChanged()

    override suspend fun refresh() = writeMutex.withLock {
        val stored = read()
        val updated = withContext(Dispatchers.Default) { resolve(stored) }
        if (updated != stored) write(updated)
    }

    override suspend fun setOverride(code: CountryCode) = writeMutex.withLock {
        val stored = read()
        write(stored.copy(override = code, overrideBasis = stored.detected?.code))
    }

    override suspend fun clearOverride() = writeMutex.withLock {
        write(read().copy(override = null, overrideBasis = null))
    }

    private suspend fun read(): StoredCountry = dataStore.data.first().toStored()

    private suspend fun write(stored: StoredCountry) {
        dataStore.edit { prefs ->
            prefs.setOrRemove(DETECTED, stored.detected?.code?.value)
            prefs.setOrRemove(DETECTED_SOURCE, stored.detected?.source?.name)
            prefs.setOrRemove(OVERRIDE, stored.override?.value)
            prefs.setOrRemove(OVERRIDE_BASIS, stored.overrideBasis?.value)
        }
    }

    private fun Preferences.toStored(): StoredCountry {
        val code = this[DETECTED]?.let(CountryCode::of)
        val source = this[DETECTED_SOURCE]?.let { name -> CountrySource.entries.firstOrNull { it.name == name } }
        return StoredCountry(
            detected = if (code != null && source != null) CurrentCountry(code, source) else null,
            override = this[OVERRIDE]?.let(CountryCode::of),
            overrideBasis = this[OVERRIDE_BASIS]?.let(CountryCode::of),
        )
    }

    private fun MutablePreferences.setOrRemove(key: Key<String>, value: String?) {
        if (value == null) remove(key) else set(key, value)
    }

    private companion object {
        val DETECTED = stringPreferencesKey("last_detected")
        val DETECTED_SOURCE = stringPreferencesKey("last_detected_source")
        val OVERRIDE = stringPreferencesKey("override")
        val OVERRIDE_BASIS = stringPreferencesKey("override_basis")
    }
}
