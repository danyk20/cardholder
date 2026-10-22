package io.github.danyk20.cardholder.core.location

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.location.LocationManagerCompat
import androidx.core.util.Consumer
import io.github.danyk20.cardholder.core.domain.location.FixPrecision
import io.github.danyk20.cardholder.core.domain.location.FixRequest
import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.domain.location.LocationFix
import java.time.Instant
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

// Framework location reads shared by the location suggestions and the country detection.
// Callers check their own permission and the Location switch before calling these.

/** Enabled providers, the best first: fused (Wi‑Fi, mobile network and GPS combined), network, GPS. */
internal fun LocationManager.enabledProviders(allowGps: Boolean = true): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasProvider(LocationManager.FUSED_PROVIDER)) {
        add(LocationManager.FUSED_PROVIDER)
    }
    add(LocationManager.NETWORK_PROVIDER)
    if (allowGps) add(LocationManager.GPS_PROVIDER)
}.filter { runCatching { isProviderEnabled(it) }.getOrDefault(false) }

/** The most accurate last known location of [request] that is recent and accurate enough, if any. */
@SuppressLint("MissingPermission")
internal fun LocationManager.lastKnownFix(request: FixRequest, allowGps: Boolean = true): LocationFix? {
    val nowNanos = SystemClock.elapsedRealtimeNanos()
    return enabledProviders(allowGps)
        .mapNotNull(::getLastKnownLocation)
        .filter { nowNanos - it.elapsedRealtimeNanos <= request.maxAge.toNanos() }
        .filter { it.hasAccuracy() && it.accuracy <= request.goodEnoughMeters }
        .minByOrNull { it.accuracy }
        ?.toFix()
}

/** One new fix from the best enabled provider, or `null` if none arrives within the timeout of [request]. */
@SuppressLint("MissingPermission")
internal suspend fun LocationManager.currentFix(
    request: FixRequest,
    executor: Executor,
    allowGps: Boolean = true,
): LocationFix? {
    val provider = enabledProviders(allowGps).firstOrNull() ?: return null
    val signal = CancellationSignal()
    return try {
        withTimeoutOrNull(request.timeout.toMillis()) {
            suspendCancellableCoroutine { continuation ->
                val deliver = { location: Location? ->
                    if (continuation.isActive) continuation.resume(location?.toFix())
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getCurrentLocation(provider, request.toLocationRequest(), signal, executor, deliver)
                } else {
                    LocationManagerCompat.getCurrentLocation(
                        this@currentFix,
                        provider,
                        signal,
                        executor,
                        Consumer<Location?>(deliver),
                    )
                }
            }
        }
    } finally {
        signal.cancel()
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun FixRequest.toLocationRequest(): LocationRequest = LocationRequest.Builder(0)
    .setQuality(
        when (precision) {
            FixPrecision.HIGH -> LocationRequest.QUALITY_HIGH_ACCURACY
            FixPrecision.BALANCED -> LocationRequest.QUALITY_BALANCED_POWER_ACCURACY
            FixPrecision.LOW -> LocationRequest.QUALITY_LOW_POWER
        },
    )
    .setDurationMillis(timeout.toMillis())
    .build()

internal fun Location.toFix() = LocationFix(GeoPoint(latitude, longitude), accuracy, Instant.ofEpochMilli(time))
