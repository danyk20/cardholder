package io.github.danyk20.cardholder.core.location

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.domain.location.AccessPoint
import io.github.danyk20.cardholder.core.domain.location.Bssid
import io.github.danyk20.cardholder.core.domain.location.FixRequest
import io.github.danyk20.cardholder.core.domain.location.LocationPermission
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowScanResult

@RunWith(AndroidJUnit4::class)
class AndroidSurroundingsProviderTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val access = AndroidLocationAccess(context) { false }
    private val provider = AndroidSurroundingsProvider(context, access)
    private val wifi = context.getSystemService(WifiManager::class.java)
    private val locations = context.getSystemService(LocationManager::class.java)

    @Before
    fun setUp() {
        shadowOf(locations).setLocationEnabled(true)
        shadowOf(locations).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
    }

    @Test
    fun `reads nothing without location permission`() = runTest {
        shadowOf(wifi).setScanResults(listOf(scanResult("a4:2b:b0:00:00:01", "Kaufland-Free", -60)))

        assertEquals(LocationPermission.NONE, access.permission())
        assertEquals(emptyList(), provider.accessPoints(Duration.ofMinutes(1)))
        assertNull(provider.locationFix(FixRequest.Nearby))
    }

    @Test
    fun `only approximate location isn't enough`() = runTest {
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(wifi).setScanResults(listOf(scanResult("a4:2b:b0:00:00:01", "Kaufland-Free", -60)))

        assertEquals(LocationPermission.APPROXIMATE, access.permission())
        assertEquals(emptyList(), provider.accessPoints(Duration.ofMinutes(1)))
    }

    @Test
    fun `uses recent scan results without scanning`() = runTest {
        grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(wifi).setScanResults(
            listOf(
                scanResult("a4:2b:b0:00:00:01", "Kaufland-Free", -60),
                scanResult("02:00:00:00:00:00", "Hidden", -50),
            ),
        )

        assertEquals(
            listOf(AccessPoint(Bssid.parse("a4:2b:b0:00:00:01")!!, "Kaufland-Free", -60)),
            provider.accessPoints(Duration.ofMinutes(1)),
        )
    }

    @Test
    fun `reuses a recent and accurate last known location`() = runTest {
        grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(locations).setLastKnownLocation(LocationManager.NETWORK_PROVIDER, location(accuracy = 25f))

        val fix = provider.locationFix(FixRequest.Nearby)

        assertEquals(48.1486, fix?.point?.latitude)
        assertEquals(25f, fix?.accuracyMeters)
    }

    @Test
    fun `knows when precise location is allowed all the time`() {
        grant(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        )

        assertEquals(LocationPermission.PRECISE_ALWAYS, access.permission())
        assertTrue(access.isLocationEnabled())
    }

    private fun grant(vararg permissions: String) = shadowOf(context).grantPermissions(*permissions)

    private fun location(accuracy: Float) = Location(LocationManager.NETWORK_PROVIDER).apply {
        latitude = 48.1486
        longitude = 17.1077
        this.accuracy = accuracy
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }

    @Suppress("DEPRECATION")
    private fun scanResult(bssid: String, ssid: String, level: Int): ScanResult =
        ShadowScanResult.newInstance(ssid, bssid, "", level, 2_412).apply {
            timestamp = SystemClock.elapsedRealtimeNanos() / 1_000
        }
}
