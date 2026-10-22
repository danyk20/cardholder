package io.github.danyk20.cardholder.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.danyk20.cardholder.core.domain.country.ApproximateLocationSource
import io.github.danyk20.cardholder.core.domain.location.FixPrecision
import io.github.danyk20.cardholder.core.domain.location.FixRequest
import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.domain.location.LocationAccess
import io.github.danyk20.cardholder.core.domain.location.LocationPermission
import java.time.Duration
import javax.inject.Inject

/**
 * The phone's coarse location, for finding its country. It never asks for a permission: with none
 * granted, or with Location switched off, it returns `null` without touching the location service.
 * Only network positioning is used, never GPS.
 */
@SuppressLint("MissingPermission")
internal class AndroidApproximateLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val access: LocationAccess,
) : ApproximateLocationSource {
    override suspend fun approximateLocation(maxAge: Duration): GeoPoint? {
        if (access.permission() == LocationPermission.NONE || !access.isLocationEnabled()) return null
        val locations = context.getSystemService(LocationManager::class.java) ?: return null
        // A country needs no accuracy beyond a rough area, so any last known location within maxAge will do.
        val request = FixRequest(FixPrecision.LOW, maxAge, goodEnoughMeters = Float.MAX_VALUE, timeout = TIMEOUT)
        val executor = ContextCompat.getMainExecutor(context)
        val fix = locations.lastKnownFix(request, allowGps = false)
            ?: locations.currentFix(request, executor, allowGps = false)
        return fix?.point
    }

    private companion object {
        val TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
