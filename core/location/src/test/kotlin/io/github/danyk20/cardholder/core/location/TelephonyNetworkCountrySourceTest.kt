package io.github.danyk20.cardholder.core.location

import android.app.Application
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.model.CountryCode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class TelephonyNetworkCountrySourceTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private val source = TelephonyNetworkCountrySource(context)

    @Before
    fun setUp() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TELEPHONY, true)
    }

    @Test
    fun `the registered network gives its country`() {
        shadowOf(telephony).setNetworkCountryIso("ch")

        assertEquals(CountryCode.of("CH"), source.networkCountry())
    }

    @Test
    fun `an empty country is no country`() {
        shadowOf(telephony).setNetworkCountryIso("")

        assertNull(source.networkCountry())
    }

    @Test
    fun `a value that is not an ISO country is no country`() {
        shadowOf(telephony).setNetworkCountryIso("xx1")

        assertNull(source.networkCountry())
    }

    @Test
    fun `a device without telephony has no network country`() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TELEPHONY, false)
        shadowOf(telephony).setNetworkCountryIso("ch")

        assertNull(source.networkCountry())
    }
}
