package io.github.danyk20.cardholder.core.domain.location

/** What the app may sense of the phone's location right now. */
interface LocationAccess {
    fun permission(): LocationPermission

    /** Whether Location is switched on in the system settings. */
    fun isLocationEnabled(): Boolean

    /** Whether arrival notifications can work here: Google Play services is available and the app may ask for them. */
    fun isArrivalSupported(): Boolean

    /** Whether the app may post notifications. */
    fun canNotify(): Boolean
}

enum class LocationPermission {
    NONE,

    /** Only approximate location: too coarse for places, and Wi‑Fi scans need precise location. */
    APPROXIMATE,

    /** Precise location while the app is in use. */
    PRECISE,

    /** Precise location all the time, which geofences need. */
    PRECISE_ALWAYS,
    ;

    val isPrecise: Boolean get() = this == PRECISE || this == PRECISE_ALWAYS
}

/** Whether learning and suggesting places can work at all right now. */
val LocationAccess.isUsable: Boolean get() = permission().isPrecise && isLocationEnabled()
