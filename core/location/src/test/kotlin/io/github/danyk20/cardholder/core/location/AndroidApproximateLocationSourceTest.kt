package io.github.danyk20.cardholder.core.location

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.domain.location.LocationPermission
import io.github.danyk20.cardholder.core.testing.repository.FakeLocationAccess
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AndroidApproximateLocationSourceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val locations = app.getSystemService(LocationManager::class.java)
    private val maxAge = Duration.ofHours(6)

    @Before
    fun setUp() {
        shadowOf(locations).setLocationEnabled(true)
        shadowOf(locations).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        shadowOf(locations).setProviderEnabled(LocationManager.GPS_PROVIDER, false)
    }

    @Test
    fun `without a location permission nothing is read from the location service`() = runTest {
        val counting = CountingContext(app)
        shadowOf(locations).setLastKnownLocation(LocationManager.NETWORK_PROVIDER, location(PARIS_LAT, PARIS_LON))
        val source = AndroidApproximateLocationSource(counting, FakeLocationAccess(LocationPermission.NONE))

        assertNull(source.approximateLocation(maxAge))
        assertEquals(0, counting.locationServiceReads)
    }

    @Test
    fun `an approximate permission is enough for the last known location`() = runTest {
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(locations).setLastKnownLocation(LocationManager.NETWORK_PROVIDER, location(PARIS_LAT, PARIS_LON))
        val source = AndroidApproximateLocationSource(app, FakeLocationAccess(LocationPermission.APPROXIMATE))

        val point = source.approximateLocation(maxAge)

        assertEquals(PARIS_LAT, point!!.latitude, 0.001)
        assertEquals(PARIS_LON, point.longitude, 0.001)
    }

    @Test
    fun `with location switched off nothing is read`() = runTest {
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(locations).setLastKnownLocation(LocationManager.NETWORK_PROVIDER, location(PARIS_LAT, PARIS_LON))
        val source = AndroidApproximateLocationSource(
            app,
            FakeLocationAccess(LocationPermission.APPROXIMATE, locationEnabled = false),
        )

        assertNull(source.approximateLocation(maxAge))
    }

    @Test
    fun `a last known location older than the age limit is not used`() = runTest {
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)
        val threeHoursAgo = Duration.ofHours(3).toNanos()
        shadowOf(locations).setLastKnownLocation(
            LocationManager.NETWORK_PROVIDER,
            location(PARIS_LAT, PARIS_LON, ageNanos = threeHoursAgo),
        )
        val source = AndroidApproximateLocationSource(app, FakeLocationAccess(LocationPermission.APPROXIMATE))

        assertNull(source.approximateLocation(Duration.ofHours(1)))
    }

    @Test
    fun `GPS alone is never used for the country`() = runTest {
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(locations).setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
        shadowOf(locations).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(locations).setLastKnownLocation(
            LocationManager.GPS_PROVIDER,
            location(PARIS_LAT, PARIS_LON, provider = LocationManager.GPS_PROVIDER),
        )
        val source = AndroidApproximateLocationSource(app, FakeLocationAccess(LocationPermission.APPROXIMATE))

        assertNull(source.approximateLocation(maxAge))
    }

    private fun grant(vararg permissions: String) {
        shadowOf(app).grantPermissions(*permissions)
    }

    private fun location(
        latitude: Double,
        longitude: Double,
        provider: String = LocationManager.NETWORK_PROVIDER,
        ageNanos: Long = 0,
    ) = Location(provider).apply {
        this.latitude = latitude
        this.longitude = longitude
        accuracy = ACCURACY_METERS
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() - ageNanos
    }

    /** Counts how often the location service is asked for. */
    private class CountingContext(base: Context) : ContextWrapper(base) {
        var locationServiceReads = 0
            private set

        override fun getSystemService(name: String): Any? {
            if (name == Context.LOCATION_SERVICE) locationServiceReads++
            return super.getSystemService(name)
        }
    }

    private companion object {
        const val PARIS_LAT = 48.8566
        const val PARIS_LON = 2.3522
        const val ACCURACY_METERS = 1_000f
    }
}
