package io.github.danyk20.cardholder.core.domain.usecase

import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.domain.location.GeofenceRegistrar
import io.github.danyk20.cardholder.core.domain.location.LocationAccess
import io.github.danyk20.cardholder.core.domain.location.LocationPermission
import io.github.danyk20.cardholder.core.domain.location.Place
import io.github.danyk20.cardholder.core.domain.location.PlaceGeofence
import io.github.danyk20.cardholder.core.domain.repository.PlaceRepository
import io.github.danyk20.cardholder.core.domain.repository.UserPreferencesRepository
import io.github.danyk20.cardholder.core.model.PlaceSuggestions
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the system's geofences in line with the learned places while arrival notifications are on,
 * and removes them otherwise. Geofences are only handed over again when they actually changed.
 */
@Singleton
class SyncGeofencesUseCase @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val locationAccess: LocationAccess,
    private val registrar: GeofenceRegistrar,
) {
    private val mutex = Mutex()

    /** What the system watches since this process registered it; `null` if nothing. */
    private var registered: List<PlaceGeofence>? = null

    /** Syncs whenever the mode or the places change; collect it for as long as the app runs. */
    fun keepUpToDate(): Flow<Unit> = combine(
        preferencesRepository.preferences.map { it.placeSuggestions }.distinctUntilChanged(),
        placeRepository.observeGeofencePlaces(MAX_GEOFENCES),
    ) { mode, places -> apply(mode, places) }

    /** Syncs once, e.g. after the phone restarted, which removes all geofences. */
    suspend fun sync() = apply(
        preferencesRepository.preferences.first().placeSuggestions,
        placeRepository.observeGeofencePlaces(MAX_GEOFENCES).first(),
    )

    /** Removes the geofences, e.g. when one fires although arrival notifications were turned off. */
    suspend fun clear() = mutex.withLock {
        registrar.clear()
        registered = null
    }

    private suspend fun apply(mode: PlaceSuggestions, places: List<Place>) = mutex.withLock {
        val wanted = places.mapNotNull(::geofenceOf).takeIf { mode == PlaceSuggestions.ARRIVAL && canWatch() }
        when {
            wanted == registered -> Unit

            wanted.isNullOrEmpty() -> {
                registrar.clear()
                registered = null
            }

            registrar.replaceAll(wanted) -> registered = wanted
        }
    }

    private fun canWatch() = locationAccess.isArrivalSupported() &&
        locationAccess.permission() == LocationPermission.PRECISE_ALWAYS &&
        locationAccess.isLocationEnabled()

    private fun geofenceOf(place: Place): PlaceGeofence? {
        val centre = place.centre ?: return null
        val radius = ((place.accuracyMeters ?: 0f) * 2).coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
        // Rounded to about 10 m, so small refinements of a centre don't re-register everything.
        return PlaceGeofence(place.id, GeoPoint(centre.latitude.rounded(), centre.longitude.rounded()), radius)
    }

    private fun Double.rounded() = toBigDecimal().setScale(COORDINATE_DECIMALS, RoundingMode.HALF_UP).toDouble()

    companion object {
        /** Android allows an app at most 100 geofences; the most used places get them. */
        const val MAX_GEOFENCES = 100

        /** Geofences smaller than this fire unreliably. */
        const val MIN_RADIUS_METERS = 150f
        const val MAX_RADIUS_METERS = 300f
        private const val COORDINATE_DECIMALS = 4
    }
}
