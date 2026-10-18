package io.github.danyk20.cardholder.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.danyk20.cardholder.core.domain.location.LocationAccess
import io.github.danyk20.cardholder.core.domain.location.LocationPermission
import javax.inject.Inject

internal class AndroidLocationAccess(private val context: Context, private val arrivalSupport: () -> Boolean) :
    LocationAccess {
    @Inject
    constructor(@ApplicationContext context: Context, geofences: GeofenceSupport) :
        this(context, geofences::isAvailable)

    override fun permission(): LocationPermission = when {
        !isGranted(Manifest.permission.ACCESS_FINE_LOCATION) -> {
            if (isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                LocationPermission.APPROXIMATE
            } else {
                LocationPermission.NONE
            }
        }

        isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) -> LocationPermission.PRECISE_ALWAYS

        else -> LocationPermission.PRECISE
    }

    override fun isLocationEnabled(): Boolean =
        context.getSystemService(LocationManager::class.java)?.let(LocationManagerCompat::isLocationEnabled) == true

    override fun isArrivalSupported(): Boolean = arrivalSupport()

    override fun canNotify(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                isGranted(Manifest.permission.POST_NOTIFICATIONS)
            )

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
