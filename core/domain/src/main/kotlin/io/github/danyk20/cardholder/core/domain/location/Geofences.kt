package io.github.danyk20.cardholder.core.domain.location

/** A circle around a place that the system watches for arrivals. */
data class PlaceGeofence(val placeId: Long, val centre: GeoPoint, val radiusMeters: Float)

/** Hands geofences to the system, which watches them while the app isn't running. */
interface GeofenceRegistrar {
    /** Replaces all watched geofences; `false` if the system refused, e.g. because Location is off. */
    suspend fun replaceAll(geofences: List<PlaceGeofence>): Boolean

    suspend fun clear()
}

/** Reacts to the user arriving at or leaving places, as reported by the system. */
interface ArrivalHandler {
    suspend fun onArrival(placeIds: List<Long>)

    suspend fun onDeparture(placeIds: List<Long>)
}
