package io.github.danyk20.cardholder.core.location.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.danyk20.cardholder.core.domain.location.GeofenceRegistrar
import io.github.danyk20.cardholder.core.domain.location.PlaceGeofence
import io.github.danyk20.cardholder.core.location.GeofenceSupport
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

/**
 * Geofences watched by Google Play services, which uses the phone's existing low-power signals; the
 * app only runs when the user stays at a place for a while or leaves it.
 */
internal class PlayServicesGeofenceRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val support: GeofenceSupport,
) : GeofenceRegistrar {
    private val client by lazy { LocationServices.getGeofencingClient(context) }

    // The caller only registers geofences with background location granted, which lint can't follow.
    @SuppressLint("MissingPermission")
    override suspend fun replaceAll(geofences: List<PlaceGeofence>): Boolean {
        if (!support.isAvailable()) return false
        return try {
            client.removeGeofences(transitions()).await()
            val request = GeofencingRequest.Builder()
                // Not on registering: right after learning a place the user is still standing in it.
                .setInitialTrigger(0)
                .addGeofences(geofences.map(::toGeofence))
                .build()
            client.addGeofences(request, transitions()).await()
            setRestoreAfterReboot(true)
            true
        } catch (e: ApiException) {
            // E.g. Location or Google's location accuracy is off; tried again on the next start.
            Log.w(TAG, "Geofences not registered: ${e.statusCode}")
            false
        }
    }

    override suspend fun clear() {
        setRestoreAfterReboot(false)
        if (!support.isAvailable()) return
        try {
            client.removeGeofences(transitions()).await()
        } catch (e: ApiException) {
            Log.w(TAG, "Geofences not removed: ${e.statusCode}")
        }
    }

    private fun toGeofence(geofence: PlaceGeofence): Geofence = Geofence.Builder()
        .setRequestId(GeofenceIds.of(geofence.placeId))
        .setCircularRegion(geofence.centre.latitude, geofence.centre.longitude, geofence.radiusMeters)
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        // Staying a while means shopping; walking or driving past doesn't notify.
        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL or Geofence.GEOFENCE_TRANSITION_EXIT)
        .setLoiteringDelay(LOITERING_MILLIS)
        // A few minutes late is fine at a shop and saves battery.
        .setNotificationResponsiveness(RESPONSIVENESS_MILLIS)
        .build()

    /**
     * One PendingIntent for all geofences, as Play services allows only a few per app; mutable, so
     * Play services can add the event to it.
     */
    private fun transitions(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, GeofenceTransitionReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    /** The restore receiver only wakes the app after a restart while there are geofences to restore. */
    private fun setRestoreAfterReboot(enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, GeofenceRestoreReceiver::class.java),
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }

    private companion object {
        const val TAG = "Geofences"
        val LOITERING_MILLIS = TimeUnit.SECONDS.toMillis(90).toInt()
        val RESPONSIVENESS_MILLIS = TimeUnit.MINUTES.toMillis(3).toInt()
    }
}

internal object GeofenceIds {
    private const val PREFIX = "place:"

    fun of(placeId: Long) = "$PREFIX$placeId"

    fun placeId(requestId: String): Long? = requestId.removePrefix(PREFIX).takeIf { it != requestId }?.toLongOrNull()
}
