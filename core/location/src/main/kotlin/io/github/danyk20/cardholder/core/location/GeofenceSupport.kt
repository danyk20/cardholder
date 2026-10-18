package io.github.danyk20.cardholder.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Whether Google Play services can watch geofences for arrival notifications on this device. */
@Singleton
internal class GeofenceSupport @Inject constructor(@ApplicationContext private val context: Context) {
    // Once available it stays so while the app runs, and settings ask on the main thread, so a positive
    // answer is kept. A negative one is asked again: Play services may still be starting after a reboot.
    @Volatile
    private var available = false

    fun isAvailable(): Boolean {
        if (!available) {
            available = declaresBackgroundLocation() &&
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }
        return available
    }

    /** A build may leave out background location (see the manifest), and with it arrival notifications. */
    private fun declaresBackgroundLocation(): Boolean = context.packageManager
        .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        .requestedPermissions.orEmpty()
        .contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
}
