package io.github.danyk20.cardholder.core.location

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.model.CountryCode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** The bundled outlines, checked on places whose country is well known. */
@RunWith(AndroidJUnit4::class)
class OfflineCountryBordersTest {
    private val borders = OfflineCountryBorders(ApplicationProvider.getApplicationContext(), Dispatchers.Unconfined)

    @Test
    fun `zurich is in switzerland`() = assertCountry("CH", 47.3769, 8.5417)

    @Test
    fun `basel is in switzerland`() = assertCountry("CH", 47.5596, 7.5886)

    @Test
    fun `geneva and lugano are in switzerland`() {
        assertCountry("CH", 46.2044, 6.1432)
        assertCountry("CH", 46.0037, 8.9511)
    }

    @Test
    fun `a town across the rhine from basel is in germany`() = assertCountry("DE", 47.5962, 7.6100)

    @Test
    fun `bratislava is in slovakia`() = assertCountry("SK", 48.1486, 17.1077)

    @Test
    fun `monaco is in monaco`() = assertCountry("MC", 43.7384, 7.4246)

    @Test
    fun `a beach at nice, just off the simplified coast, is in france`() = assertCountry("FR", 43.6945, 7.2640)

    @Test
    fun `places in france, germany, austria and italy are found`() {
        assertCountry("FR", 48.8566, 2.3522)
        assertCountry("DE", 52.5200, 13.4050)
        assertCountry("AT", 48.2082, 16.3738)
        assertCountry("IT", 45.4642, 9.1900)
    }

    @Test
    fun `non-iso territories and far countries are found`() {
        assertCountry("XK", 42.6629, 21.1655)
        assertCountry("NO", 59.9139, 10.7522)
        assertCountry("NO", 78.2232, 15.6267)
        assertCountry("IE", 53.3498, -6.2603)
        assertCountry("IS", 64.1466, -21.9426)
        assertCountry("JP", 35.6762, 139.6503)
        assertCountry("AU", -33.8688, 151.2093)
        assertCountry("ZA", -33.9249, 18.4241)
    }

    @Test
    fun `the open atlantic is no country`() = runTest {
        assertNull(borders.countryAt(GeoPoint(30.0, -40.0)))
    }

    private fun assertCountry(expected: String, latitude: Double, longitude: Double) = runTest {
        assertEquals(
            CountryCode.of(expected),
            borders.countryAt(GeoPoint(latitude, longitude)),
            "($latitude, $longitude)",
        )
    }
}
