package io.github.danyk20.cardholder.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Whether Google Play services can watch geofences for arrival notifications on this device. */
internal class GeofenceSupport @Inject constructor(@ApplicationContext private val context: Context) {
    fun isAvailable(): Boolean = declaresBackgroundLocation() &&
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    /** A build may leave out background location (see the manifest), and with it arrival notifications. */
    private fun declaresBackgroundLocation(): Boolean = context.packageManager
        .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        .requestedPermissions.orEmpty()
        .contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
}
