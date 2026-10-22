package io.github.danyk20.cardholder.core.location

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.danyk20.cardholder.core.domain.country.CountryBorders
import io.github.danyk20.cardholder.core.domain.di.IoDispatcher
import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.model.CountryCode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Maps a point to its country with the outlines bundled in the app, so nothing is sent anywhere.
 * The file is read on the first lookup only, which the usual path (the network country) never needs.
 */
@Singleton
internal class OfflineCountryBorders @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CountryBorders {
    private val loadMutex = Mutex()

    @Volatile
    private var lookup: CountryLookup? = null

    override suspend fun countryAt(point: GeoPoint): CountryCode? = withContext(ioDispatcher) {
        loaded().countryAt(point)
    }

    private suspend fun loaded(): CountryLookup = lookup ?: loadMutex.withLock {
        lookup ?: CountryLookup(readAsset()).also { lookup = it }
    }

    private fun readAsset(): List<CountryBorder> =
        CountryBorderFormat.decode(context.assets.open(ASSET).use { it.readBytes() })

    private companion object {
        const val ASSET = "country_borders.bin"
    }
}
