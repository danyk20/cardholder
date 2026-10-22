package io.github.danyk20.cardholder.core.location

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.util.Consumer
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.danyk20.cardholder.core.domain.location.AccessPoint
import io.github.danyk20.cardholder.core.domain.location.Bssid
import io.github.danyk20.cardholder.core.domain.location.FixPrecision
import io.github.danyk20.cardholder.core.domain.location.FixRequest
import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.domain.location.LocationAccess
import io.github.danyk20.cardholder.core.domain.location.LocationFix
import io.github.danyk20.cardholder.core.domain.location.SurroundingsProvider
import io.github.danyk20.cardholder.core.domain.location.isUsable
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reads Wi‑Fi scan results and location with the framework APIs, so it works without Google Play
 * services. Nothing is requested in the background: each call reads once and stops listening.
 */
// Every read checks LocationAccess.isUsable first, which lint can't follow.
@SuppressLint("MissingPermission")
internal class AndroidSurroundingsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val access: LocationAccess,
) : SurroundingsProvider {
    private val wifi: WifiManager? get() = context.getSystemService(WifiManager::class.java)
    private val locations: LocationManager? get() = context.getSystemService(LocationManager::class.java)
    private val executor: Executor get() = ContextCompat.getMainExecutor(context)

    override suspend fun accessPoints(maxAge: Duration): List<AccessPoint> {
        val wifi = wifi?.takeIf { access.isUsable } ?: return emptyList()
        // Android scans on its own while the screen is on, so recent results usually cost nothing.
        val cached = wifi.recentResults(maxAge)
        if (cached.isNotEmpty()) return cached
        return if (wifi.scan()) wifi.recentResults(maxAge) else wifi.recentResults(STALE_RESULTS)
    }

    override suspend fun locationFix(request: FixRequest): LocationFix? {
        val locations = locations?.takeIf { access.isUsable } ?: return null
        return locations.lastKnownFix(request) ?: locations.currentFix(request, executor)
    }

    private fun WifiManager.recentResults(maxAge: Duration): List<AccessPoint> {
        val nowMicros = SystemClock.elapsedRealtimeNanos() / NANOS_PER_MICRO
        val maxAgeMicros = maxAge.toNanos() / NANOS_PER_MICRO
        return scanResults.orEmpty()
            .filter { nowMicros - it.timestamp <= maxAgeMicros }
            .mapNotNull { result -> Bssid.parse(result.BSSID)?.let { AccessPoint(it, result.ssid(), result.level) } }
    }

    /** Runs one scan; `false` if Android refused (throttled, Wi‑Fi scanning off) or it took too long. */
    private suspend fun WifiManager.scan(): Boolean {
        var receiver: BroadcastReceiver? = null
        return try {
            withTimeoutOrNull(SCAN_TIMEOUT.toMillis()) {
                suspendCancellableCoroutine { continuation ->
                    receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            val updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                            if (continuation.isActive) continuation.resume(updated)
                        }
                    }.also {
                        ContextCompat.registerReceiver(
                            context,
                            it,
                            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                            ContextCompat.RECEIVER_NOT_EXPORTED,
                        )
                    }
                    // Deprecated for apps, but still the only way to ask for a scan; Android limits it.
                    @Suppress("DEPRECATION")
                    if (!startScan() && continuation.isActive) continuation.resume(false)
                }
            } ?: false
        } finally {
            receiver?.let(context::unregisterReceiver)
        }
    }

    private fun ScanResult.ssid(): String? {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && wifiSsid != null) {
            // Quoted when the name is text, hex digits otherwise.
            wifiSsid.toString().takeIf {
                it.length >= 2 && it.startsWith('"') && it.endsWith('"')
            }?.drop(1)?.dropLast(1)
        } else {
            @Suppress("DEPRECATION")
            SSID
        }
        return name?.takeIf { it.isNotBlank() && it != UNKNOWN_SSID }
    }

    private companion object {
        const val NANOS_PER_MICRO = 1_000L
        val SCAN_TIMEOUT: Duration = Duration.ofSeconds(6)

        /** When a new scan isn't possible, somewhat older results still beat none. */
        val STALE_RESULTS: Duration = Duration.ofMinutes(5)
        const val UNKNOWN_SSID = "<unknown ssid>"
    }
}
