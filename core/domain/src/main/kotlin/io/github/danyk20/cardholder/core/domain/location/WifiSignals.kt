package io.github.danyk20.cardholder.core.domain.location

/** Rules for judging Wi‑Fi access points as signs of a place. */
object WifiSignals {
    /** Weaker access points are too far away to say anything about the place. */
    const val MIN_STORED_RSSI = -85

    /** At most this many access points are kept per place; the strongest ones of a scan. */
    const val MAX_STORED = 20

    /** Access points at least this strong are in the same building, give or take. */
    const val STRONG_RSSI = -70

    /** Network names only count from this strength on, so a name seen down the street doesn't. */
    const val GOOD_RSSI = -80

    private const val STRONG_WEIGHT = 1.0
    private const val GOOD_WEIGHT = 0.6
    private const val WEAK_WEIGHT = 0.3

    /** Locally administered addresses may move (phone hotspots), so they count half. */
    private const val LOCAL_FACTOR = 0.5

    /** Names of phone hotspots and other networks that travel with someone. */
    private val HotspotPrefixes = listOf(
        "androidap", "android_", "android-", "iphone", "ipad", "galaxy", "redmi", "xiaomi", "huawei", "honor",
        "oneplus", "pixel", "moto", "oppo", "realme", "vivo", "nokia", "direct-",
    )
    private val HotspotParts = listOf("iphone", "ipad", "'s ", "’s ")

    /** Networks found in many places: operators' and transport hotspots, campus networks. */
    private val SharedNetworks = setOf(
        "eduroam", "telekom", "vodafone", "swisscom", "xfinitywifi", "btwifi", "fon", "upc", "wifionice", "oebb",
        "cdwifi", "regiojet", "flixbus", "freewifi", "sbb",
    )

    /** Words that don't identify a place on their own. */
    private val GenericWords = setOf(
        "free", "wifi", "wi", "fi", "wlan", "guest", "guests", "gast", "gaste", "gaeste", "public", "hotspot",
        "internet", "open", "customer", "customers", "kunden", "kunde", "besucher", "visitor", "visitors", "net",
        "network", "secure", "ext", "extender", "repeater", "home", "office", "mobile", "ghz", "g", "the", "my",
    )

    /** The access points of a scan worth remembering: strong enough, not travelling and not [ignored]. */
    fun usable(accessPoints: List<AccessPoint>, ignored: Set<Bssid>): List<AccessPoint> = accessPoints
        .filter { it.rssi >= MIN_STORED_RSSI && it.bssid !in ignored && !isHotspot(it.ssid) }
        .sortedByDescending { it.rssi }
        .distinctBy { it.bssid }
        .take(MAX_STORED)

    /** How much seeing [accessPoint] again says that the phone is at the place where it was stored. */
    fun weight(accessPoint: AccessPoint): Double {
        val strength = when {
            accessPoint.rssi >= STRONG_RSSI -> STRONG_WEIGHT
            accessPoint.rssi >= GOOD_RSSI -> GOOD_WEIGHT
            else -> WEAK_WEIGHT
        }
        return if (accessPoint.bssid.isLocallyAdministered) strength * LOCAL_FACTOR else strength
    }

    /** Whether [ssid] looks like a phone's hotspot or another network that moves around. */
    fun isHotspot(ssid: String?): Boolean {
        val name = ssid?.let(Words::fold) ?: return false
        return HotspotPrefixes.any { name.startsWith(it) } || HotspotParts.any { it in name }
    }

    /** Whether [ssid] is too common to identify a shop chain, e.g. "Free WiFi" or "eduroam". */
    fun isGeneric(ssid: String?): Boolean {
        if (ssid.isNullOrBlank() || isHotspot(ssid)) return true
        val tokens = Words.tokens(ssid)
        if (Words.segments(ssid).any { Words.fold(it) in SharedNetworks } || tokens.any { it in SharedNetworks }) {
            return true
        }
        return tokens.all { it in GenericWords || it.all(Char::isDigit) }
    }
}
