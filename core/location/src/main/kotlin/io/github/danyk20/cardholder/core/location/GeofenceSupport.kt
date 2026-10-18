package io.github.danyk20.cardholder.core.location

import javax.inject.Inject

/** Whether the system can watch geofences for arrival notifications on this device. */
internal class GeofenceSupport @Inject constructor() {
    // Arrival notifications come with Google Play services' geofences.
    @Suppress("FunctionOnlyReturningConstant")
    fun isAvailable(): Boolean = false
}
