package io.github.danyk20.cardholder.core.domain.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** A weighted centre of location fixes and how far off it may be. */
data class Centre(val point: GeoPoint, val accuracyMeters: Float)

object GeoMath {
    private const val EARTH_RADIUS_METERS = 6_371_008.8
    private const val HALF_TURN = 180.0
    private const val FULL_TURN = 360.0

    /** No fix is trusted to be more accurate than this, so one lucky reading can't dominate. */
    private const val MIN_ACCURACY_METERS = 5f
    private const val MIN_FIXES_FOR_OUTLIERS = 3
    private const val OUTLIER_METERS = 150.0
    private const val OUTLIER_ACCURACIES = 3

    /** Great-circle distance between [a] and [b] in metres. */
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val halfDLat = (lat2 - lat1) / 2
        val halfDLon = Math.toRadians(b.longitude - a.longitude) / 2
        val h = sin(halfDLat) * sin(halfDLat) + cos(lat1) * cos(lat2) * sin(halfDLon) * sin(halfDLon)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /**
     * The centre of [fixes], weighting each by its accuracy so precise fixes count more. Once there are
     * a few fixes, those far from their median are dropped as outliers first.
     */
    fun centre(fixes: List<LocationFix>): Centre? {
        if (fixes.size < MIN_FIXES_FOR_OUTLIERS) return weightedCentre(fixes)
        val median = median(fixes.map { it.point })
        val kept = fixes.filter {
            distanceMeters(it.point, median) <= max(OUTLIER_METERS, OUTLIER_ACCURACIES * it.accuracyMeters.toDouble())
        }
        return weightedCentre(kept.ifEmpty { fixes })
    }

    private fun median(points: List<GeoPoint>): GeoPoint {
        val reference = points.first().longitude
        return GeoPoint(
            latitude = points.map { it.latitude }.median(),
            longitude = wrap(reference + points.map { wrap(it.longitude - reference) }.median()),
        )
    }

    private fun List<Double>.median(): Double {
        val sorted = sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }

    private fun weightedCentre(fixes: List<LocationFix>): Centre? {
        if (fixes.isEmpty()) return null
        // Longitudes are averaged relative to the first fix, so places on the antimeridian work too.
        val reference = fixes.first().point.longitude
        var totalWeight = 0.0
        var latitude = 0.0
        var longitudeOffset = 0.0
        for (fix in fixes) {
            val accuracy = max(fix.accuracyMeters, MIN_ACCURACY_METERS).toDouble()
            val weight = 1 / (accuracy * accuracy)
            totalWeight += weight
            latitude += weight * fix.point.latitude
            longitudeOffset += weight * wrap(fix.point.longitude - reference)
        }
        return Centre(
            point = GeoPoint(latitude / totalWeight, wrap(reference + longitudeOffset / totalWeight)),
            accuracyMeters = (1 / sqrt(totalWeight)).toFloat(),
        )
    }

    /** Brings a longitude (difference) into -180..180 degrees. */
    private fun wrap(degrees: Double): Double = ((degrees + HALF_TURN) % FULL_TURN + FULL_TURN) % FULL_TURN - HALF_TURN
}
