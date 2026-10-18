package io.github.danyk20.cardholder.core.domain.location

import java.time.Duration
import java.time.Instant

/** What to store for one use of a card. */
data class VisitPlan(
    /** The card's place the visit belongs to, or `null` for a new place. */
    val placeId: Long?,
    /** The fix to store, if it was accurate enough. */
    val fix: LocationFix?,
    /**
     * The card was used at this place moments ago: the visit continues, so [fix] only replaces the
     * last stored fix if it's more accurate, and the place's use count stays.
     */
    val sameVisit: Boolean,
    val accessPoints: List<AccessPoint>,
    /** Access points also stored at places far away: they travel (buses, trains) and are forgotten everywhere. */
    val travelling: Set<Bssid>,
)

/** Decides which place a use of a card belongs to. */
object PlaceClustering {
    /** A place keeps only its latest fixes, so its centre follows a shop that moved. */
    const val MAX_FIXES = 10

    /** A fix within this distance of one of the card's places belongs to it. */
    const val JOIN_METERS = 150.0

    /** Less accurate fixes aren't stored; the Wi‑Fi seen still is. */
    const val MAX_STORED_ACCURACY_METERS = 100f

    /** An access point stored at places this far apart can't be fixed in one place. */
    const val TRAVELLING_METERS = 1_000.0

    /** Uses closer together than this are one visit, so ten fixes stand for ten visits. */
    val SameVisit: Duration = Duration.ofMinutes(15)

    /**
     * Plans storing [surroundings] for a card with the existing [cardPlaces].
     *
     * @param knownPlaces places of any card that store one of the scanned access points.
     * @param travelling access points already known to travel.
     * @return `null` when nothing worth storing was sensed.
     */
    fun plan(
        cardPlaces: List<Place>,
        knownPlaces: List<Place>,
        surroundings: Surroundings,
        travelling: Set<Bssid>,
        now: Instant,
    ): VisitPlan? {
        val fix = surroundings.fix?.takeIf { it.accuracyMeters <= MAX_STORED_ACCURACY_METERS }
        val newlyTravelling = fix?.let { travellingAccessPoints(knownPlaces + cardPlaces, surroundings, it) }.orEmpty()
        val accessPoints = WifiSignals.usable(surroundings.accessPoints, travelling + newlyTravelling)
        if (fix == null && accessPoints.isEmpty()) return null

        val place = placeSharingAccessPoints(cardPlaces, accessPoints) ?: fix?.let { nearestPlace(cardPlaces, it) }
        return VisitPlan(
            placeId = place?.id,
            fix = fix,
            sameVisit = place != null && Duration.between(place.lastUsedAt, now) < SameVisit,
            accessPoints = accessPoints,
            travelling = newlyTravelling,
        )
    }

    private fun travellingAccessPoints(places: List<Place>, surroundings: Surroundings, fix: LocationFix): Set<Bssid> {
        val scanned = surroundings.accessPoints.mapTo(HashSet()) { it.bssid }
        return places
            .filter { place ->
                place.centre != null && GeoMath.distanceMeters(place.centre, fix.point) > TRAVELLING_METERS
            }
            .flatMap { place -> place.accessPoints.map { it.bssid } }
            .filterTo(HashSet()) { it in scanned }
    }

    private fun placeSharingAccessPoints(places: List<Place>, accessPoints: List<AccessPoint>): Place? {
        val seen = accessPoints.mapTo(HashSet()) { it.bssid }
        return places
            .map { place -> place to place.accessPoints.count { it.bssid in seen } }
            .filter { (_, shared) -> shared > 0 }
            .maxByOrNull { (_, shared) -> shared }
            ?.first
    }

    private fun nearestPlace(places: List<Place>, fix: LocationFix): Place? = places
        .mapNotNull { place -> place.centre?.let { place to GeoMath.distanceMeters(it, fix.point) } }
        .filter { (_, meters) -> meters <= JOIN_METERS }
        .minByOrNull { (_, meters) -> meters }
        ?.first
}
