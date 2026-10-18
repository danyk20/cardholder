package io.github.danyk20.cardholder.core.domain.location

import io.github.danyk20.cardholder.core.model.Card

/** A card that may be suggested, with where it was used and how its shop shows up in network names. */
data class SuggestionCandidate(val card: Card, val places: List<Place>, val keywords: Set<String>)

/** Ranks cards by how likely the user is at a place where they use them. */
object NearbyMatcher {
    const val MAX_SUGGESTIONS = 3

    /** Cards used within this distance are suggested. */
    const val NEARBY_METERS = 1_000.0

    /** A fix less accurate than this can't tell what's within [NEARBY_METERS]. */
    const val MAX_FIX_ACCURACY_METERS = 500f

    /** A place scoring this much on Wi‑Fi alone is certain enough that no location is needed. */
    const val CERTAIN_SCORE = 1.0

    /** A network name only identifies a chain once it was seen at this many of the card's places. */
    private const val CHAIN_PLACES = 2

    /** Whether some place scores [CERTAIN_SCORE] on [scan] alone, so reading the location can be skipped. */
    fun isCertain(candidates: List<SuggestionCandidate>, scan: List<AccessPoint>): Boolean {
        val seen = scan.associateBy { it.bssid }
        return candidates.any { candidate -> candidate.places.any { score(it, seen) >= CERTAIN_SCORE } }
    }

    /** At most [MAX_SUGGESTIONS] cards that seem to be used nearby, the most likely first. */
    fun match(candidates: List<SuggestionCandidate>, scan: List<AccessPoint>, fix: LocationFix?): List<NearbyCard> {
        val seen = scan.associateBy { it.bssid }
        val named = scan.filter {
            it.ssid != null && it.rssi >= WifiSignals.GOOD_RSSI && !WifiSignals.isHotspot(it.ssid)
        }
        val usableFix = fix?.takeIf { it.accuracyMeters <= MAX_FIX_ACCURACY_METERS }
        return candidates
            .mapNotNull { candidate -> reasonFor(candidate, seen, named, usableFix)?.let { candidate to it } }
            .sortedWith(
                compareBy<Pair<SuggestionCandidate, NearbyReason>> { (_, reason) -> tier(reason) }
                    .thenBy { (_, reason) -> orderWithinTier(reason) }
                    .thenByDescending { (candidate, _) -> candidate.card.useCount },
            )
            .take(MAX_SUGGESTIONS)
            .map { (candidate, reason) -> NearbyCard(candidate.card.id, reason) }
    }

    private fun reasonFor(
        candidate: SuggestionCandidate,
        seen: Map<Bssid, AccessPoint>,
        named: List<AccessPoint>,
        fix: LocationFix?,
    ): NearbyReason? {
        val here = candidate.places.maxOfOrNull { score(it, seen) } ?: 0.0
        if (here > 0) return NearbyReason.WifiHere(here)
        val chainNames = chainNames(candidate.places)
        val chain = named.filter { it.ssid in chainNames }.maxOfOrNull { it.rssi }
        val shop = named.filter {
            ShopKeywords.matches(checkNotNull(it.ssid), candidate.keywords)
        }.maxOfOrNull { it.rssi }
        return chain?.let(NearbyReason::WifiChain)
            ?: shop?.let(NearbyReason::WifiName)
            ?: fix?.let { distance(candidate.places, it) }?.let(NearbyReason::Distance)
    }

    private fun score(place: Place, seen: Map<Bssid, AccessPoint>): Double =
        place.accessPoints.sumOf { stored -> seen[stored.bssid]?.let(WifiSignals::weight) ?: 0.0 }

    /** Distinctive network names seen at several of the card's places, i.e. in several branches. */
    private fun chainNames(places: List<Place>): Set<String> = places
        .flatMap { place -> place.accessPoints.mapNotNull { it.ssid }.filterNot(WifiSignals::isGeneric).distinct() }
        .groupingBy { it }
        .eachCount()
        .filterValues { it >= CHAIN_PLACES }
        .keys

    private fun distance(places: List<Place>, fix: LocationFix): Double? = places
        .mapNotNull { place -> place.centre?.let { GeoMath.distanceMeters(it, fix.point) } }
        .minOrNull()
        ?.takeIf { it <= NEARBY_METERS }

    /** Wi‑Fi of the very place first, then networks of the shop, then the nearest places. */
    private fun tier(reason: NearbyReason): Int = when (reason) {
        is NearbyReason.WifiHere -> 0
        is NearbyReason.WifiChain, is NearbyReason.WifiName -> 1
        is NearbyReason.Distance -> 2
    }

    /** Smaller comes first: higher scores, stronger signals, shorter distances. */
    private fun orderWithinTier(reason: NearbyReason): Double = when (reason) {
        is NearbyReason.WifiHere -> -reason.score
        is NearbyReason.WifiChain -> -reason.rssi.toDouble()
        is NearbyReason.WifiName -> -reason.rssi.toDouble()
        is NearbyReason.Distance -> reason.meters
    }
}
