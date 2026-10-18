package io.github.danyk20.cardholder.core.testing.repository

import io.github.danyk20.cardholder.core.domain.location.AccessPoint
import io.github.danyk20.cardholder.core.domain.location.FixRequest
import io.github.danyk20.cardholder.core.domain.location.GeofenceRegistrar
import io.github.danyk20.cardholder.core.domain.location.LocationAccess
import io.github.danyk20.cardholder.core.domain.location.LocationFix
import io.github.danyk20.cardholder.core.domain.location.LocationPermission
import io.github.danyk20.cardholder.core.domain.location.PlaceGeofence
import io.github.danyk20.cardholder.core.domain.location.SurroundingsProvider
import java.time.Duration

/** [SurroundingsProvider] returning what tests set, counting how often each sensor was read. */
class FakeSurroundingsProvider : SurroundingsProvider {
    var accessPoints: List<AccessPoint> = emptyList()
    var fix: LocationFix? = null
    var wifiReads = 0
        private set
    var fixReads = 0
        private set
    val fixRequests = mutableListOf<FixRequest>()

    override suspend fun accessPoints(maxAge: Duration): List<AccessPoint> {
        wifiReads++
        return accessPoints
    }

    override suspend fun locationFix(request: FixRequest): LocationFix? {
        fixReads++
        fixRequests += request
        return fix
    }
}

class FakeLocationAccess(
    var permission: LocationPermission = LocationPermission.PRECISE,
    var locationEnabled: Boolean = true,
    var arrivalSupported: Boolean = true,
    var notificationsAllowed: Boolean = true,
) : LocationAccess {
    override fun permission() = permission

    override fun isLocationEnabled() = locationEnabled

    override fun isArrivalSupported() = arrivalSupported

    override fun canNotify() = notificationsAllowed
}

class FakeGeofenceRegistrar : GeofenceRegistrar {
    /** What the system currently watches; `null` before anything was registered or after clearing. */
    var registered: List<PlaceGeofence>? = null
        private set
    var replaceCalls = 0
        private set
    var accepts = true

    override suspend fun replaceAll(geofences: List<PlaceGeofence>): Boolean {
        replaceCalls++
        if (accepts) registered = geofences
        return accepts
    }

    override suspend fun clear() {
        registered = null
    }
}
