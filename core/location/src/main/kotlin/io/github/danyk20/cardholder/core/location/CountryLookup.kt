package io.github.danyk20.cardholder.core.location

import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.model.CountryCode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Finds the country of a point. Inside an outline is exact (even-odd rule, so holes work). Points just
 * off the simplified coastline, such as a beach or a harbour, take the country whose edge is nearest,
 * if that edge is within [COAST_TOLERANCE_KM]. Anything farther out, such as the open sea, is no country.
 */
internal class CountryLookup(private val countries: List<CountryBorder>) {
    fun countryAt(point: GeoPoint): CountryCode? = containing(point) ?: nearestWithin(point)

    private fun containing(point: GeoPoint): CountryCode? = countries.firstOrNull { country ->
        point.latitude in country.minLat..country.maxLat &&
            point.longitude in country.minLon..country.maxLon &&
            country.polygons.any { it.contains(point) }
    }?.code

    private fun nearestWithin(point: GeoPoint): CountryCode? {
        val latMargin = COAST_TOLERANCE_KM / KM_PER_DEGREE_LAT
        val lonMargin = COAST_TOLERANCE_KM / kmPerDegreeLon(point.latitude)
        var best: CountryCode? = null
        var bestKm = COAST_TOLERANCE_KM
        for (country in countries) {
            val near = point.latitude in country.minLat - latMargin..country.maxLat + latMargin &&
                point.longitude in country.minLon - lonMargin..country.maxLon + lonMargin
            if (!near) continue
            for (polygon in country.polygons) {
                for (ring in polygon.rings) {
                    val km = ring.nearestEdgeKm(point)
                    if (km < bestKm) {
                        bestKm = km
                        best = country.code
                    }
                }
            }
        }
        return best
    }

    private companion object {
        const val COAST_TOLERANCE_KM = 25.0
        const val KM_PER_DEGREE_LAT = 110.574
        const val KM_PER_DEGREE_LON_AT_EQUATOR = 111.320
        const val MIN_COS = 0.01

        fun kmPerDegreeLon(latitude: Double): Double =
            KM_PER_DEGREE_LON_AT_EQUATOR * max(cos(Math.toRadians(latitude)), MIN_COS)
    }
}

/** Even-odd test over all rings of the polygon: a point in a hole is outside. */
private fun BorderPolygon.contains(point: GeoPoint): Boolean {
    var inside = false
    for (ring in rings) {
        var j = ring.size - 1
        for (i in 0 until ring.size) {
            val yi = ring.lat[i]
            val xi = ring.lon[i]
            val yj = ring.lat[j]
            val xj = ring.lon[j]
            val crosses = (yi > point.latitude) != (yj > point.latitude) &&
                point.longitude < (xj - xi) * (point.latitude - yi) / (yj - yi) + xi
            if (crosses) inside = !inside
            j = i
        }
    }
    return inside
}

/** Distance in km from [point] to the nearest edge of the ring, in a local flat map around the point. */
private fun BorderRing.nearestEdgeKm(point: GeoPoint): Double {
    val kmLon = 111.320 * max(cos(Math.toRadians(point.latitude)), 0.01)
    val kmLat = 110.574
    var best = Double.MAX_VALUE
    for (i in 0 until size) {
        val j = (i + 1) % size
        val ax = (lon[i] - point.longitude) * kmLon
        val ay = (lat[i] - point.latitude) * kmLat
        val bx = (lon[j] - point.longitude) * kmLon
        val by = (lat[j] - point.latitude) * kmLat
        best = min(best, distanceToSegment(ax, ay, bx, by))
    }
    return best
}

/** Distance from the origin to the segment from (ax, ay) to (bx, by). */
private fun distanceToSegment(ax: Double, ay: Double, bx: Double, by: Double): Double {
    val dx = bx - ax
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0.0) 0.0 else (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
    return hypot(ax + t * dx, ay + t * dy)
}
