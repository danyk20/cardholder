package io.github.danyk20.cardholder.core.domain.location

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class WifiSignalsTest {
    @Test
    fun `parses and prints addresses`() {
        val bssid = Bssid.parse("A4:2B:B0:01:02:FF")

        assertEquals(0xA42BB00102FFL, bssid?.value)
        assertEquals("a4:2b:b0:01:02:ff", bssid.toString())
    }

    @Test
    fun `rejects malformed and placeholder addresses`() {
        assertNull(Bssid.parse(null))
        assertNull(Bssid.parse("a4:2b:b0:01:02"))
        assertNull(Bssid.parse("a4:2b:b0:01:02:zz"))
        assertNull(Bssid.parse("02:00:00:00:00:00"))
        assertNull(Bssid.parse("00:00:00:00:00:00"))
    }

    @Test
    fun `recognises locally administered addresses`() {
        assertTrue(Bssid.parse("06:2b:b0:01:02:03")!!.isLocallyAdministered)
        assertTrue(Bssid.parse("da:a1:19:01:02:03")!!.isLocallyAdministered)
        assertFalse(Bssid.parse("a4:2b:b0:01:02:03")!!.isLocallyAdministered)
    }

    @Test
    fun `weighs access points by strength and kind`() {
        assertEquals(1.0, WifiSignals.weight(ap(1, rssi = -55)))
        assertEquals(0.6, WifiSignals.weight(ap(1, rssi = -75)))
        assertEquals(0.3, WifiSignals.weight(ap(1, rssi = -88)))
        assertEquals(0.5, WifiSignals.weight(ap(0x06_00_00_00_00_01L, rssi = -55)))
    }

    @Test
    fun `keeps the strongest usable access points`() {
        val scan = (1L..30L).map { ap(it, rssi = -40 - it.toInt()) } +
            ap(100, rssi = -90) + ap(101, "AndroidAP_1234") + ap(102, "Jane’s iPhone")

        val usable = WifiSignals.usable(scan, ignored = setOf(Bssid(1)))

        assertEquals((2L..21L).map(::Bssid), usable.map { it.bssid })
    }

    @Test
    fun `recognises phone hotspots`() {
        listOf("AndroidAP_5f2a", "iPhone de Jana", "Jana's iPhone", "Galaxy A52 1234", "DIRECT-ab-HP Printer")
            .forEach { assertTrue(WifiSignals.isHotspot(it), it) }
        listOf("Kaufland-Free", "Lidl Plus WiFi", "IKEA Guest").forEach { assertFalse(WifiSignals.isHotspot(it), it) }
    }

    @Test
    fun `recognises generic network names`() {
        listOf("Free WiFi", "Free_WiFi_5G", "eduroam", "Telekom_FON", "WIFIonICE", "SBB-FREE", "Guest", "", null)
            .forEach { assertTrue(WifiSignals.isGeneric(it), it.toString()) }
        listOf("Kaufland-Free", "LP-Guest-WLAN", "MigrosFreeWiFi", "Billa")
            .forEach { assertFalse(WifiSignals.isGeneric(it), it) }
    }
}
