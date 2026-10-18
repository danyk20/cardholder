package io.github.danyk20.cardholder.core.domain.location

import java.time.Instant

/** A position on Earth in WGS-84 degrees. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** A position reading; [accuracyMeters] is the radius Android reports with 68 % confidence. */
data class LocationFix(val point: GeoPoint, val accuracyMeters: Float, val time: Instant)

/** The hardware address of a Wi‑Fi access point as a 48-bit number. */
@JvmInline
value class Bssid(val value: Long) {
    /**
     * Set on addresses that weren't assigned by a manufacturer: phone hotspots, but also the extra
     * networks one access point broadcasts (e.g. a shop's guest network).
     */
    val isLocallyAdministered: Boolean get() = (value shr LOCAL_BIT_SHIFT) and 1L == 1L

    override fun toString(): String =
        (BYTES - 1 downTo 0).joinToString(":") { "%02x".format((value shr (it * Byte.SIZE_BITS)) and BYTE_MASK) }

    companion object {
        private const val BYTES = 6
        private const val BYTE_MASK = 0xFFL
        private const val HEX = 16
        private const val LOCAL_BIT_SHIFT = 41

        /** What Android reports instead of the real address when the app may not see it. */
        private val Hidden = Bssid(0x02_00_00_00_00_00L)

        /** Parses `aa:bb:cc:dd:ee:ff`; `null` for anything else and for placeholder addresses. */
        fun parse(text: String?): Bssid? {
            val bytes = text?.split(':')
                ?.takeIf { parts -> parts.size == BYTES && parts.all { it.length == 2 } }
                ?.map { it.toLongOrNull(HEX) ?: return null }
                ?: return null
            val bssid = Bssid(bytes.fold(0L) { acc, byte -> (acc shl Byte.SIZE_BITS) or byte })
            return bssid.takeIf { it.value != 0L && it != Hidden }
        }
    }
}

/** A Wi‑Fi network seen in a scan. [ssid] is `null` for hidden networks. */
data class AccessPoint(val bssid: Bssid, val ssid: String?, val rssi: Int)

/** What the phone senses around it at one moment. */
data class Surroundings(val accessPoints: List<AccessPoint>, val fix: LocationFix?)
