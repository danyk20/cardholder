package io.github.danyk20.cardholder.core.location.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.danyk20.cardholder.core.domain.di.ApplicationScope
import io.github.danyk20.cardholder.core.domain.location.ArrivalHandler
import io.github.danyk20.cardholder.core.domain.usecase.SyncGeofencesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "Geofences"

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface GeofenceEntryPoint {
    fun arrivalHandler(): ArrivalHandler

    fun syncGeofences(): SyncGeofencesUseCase

    @ApplicationScope
    fun applicationScope(): CoroutineScope
}

/** Runs [block] in the application scope while keeping the broadcast alive until it's done. */
private fun BroadcastReceiver.handle(context: Context, block: suspend GeofenceEntryPoint.() -> Unit) {
    val entryPoint = EntryPointAccessors.fromApplication(context, GeofenceEntryPoint::class.java)
    val pending = goAsync()
    entryPoint.applicationScope().launch {
        try {
            entryPoint.block()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // E.g. the cards can't be read; the next arrival tries again.
            Log.w(TAG, "Couldn't handle a geofence event", e)
        } finally {
            pending.finish()
        }
    }
}

/** Receives Google Play services' reports of staying at or leaving a learned place. */
internal class GeofenceTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null || event.hasError()) return
        val placeIds = event.triggeringGeofences.orEmpty().mapNotNull { GeofenceIds.placeId(it.requestId) }
        if (placeIds.isEmpty()) return
        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_DWELL -> handle(context) { arrivalHandler().onArrival(placeIds) }
            Geofence.GEOFENCE_TRANSITION_EXIT -> handle(context) { arrivalHandler().onDeparture(placeIds) }
        }
    }
}

/** Registers the geofences again after the phone restarted or the app was updated. */
internal class GeofenceRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            handle(context) { syncGeofences().sync() }
        }
    }
}
