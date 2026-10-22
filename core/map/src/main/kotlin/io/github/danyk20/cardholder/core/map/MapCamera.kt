package io.github.danyk20.cardholder.core.map

import io.github.danyk20.cardholder.core.domain.location.GeoPoint

/** Where the map shows: what the user sees when it's first shown, or after a change of places. */
sealed interface MapCamera {
    /** Shows every marker, or the whole world without any. */
    data object FitAll : MapCamera

    /**
     * Centres on [point] at [zoom]. Instances are compared by identity, so focusing the same place
     * again moves the map back to it even after the user panned away.
     */
    class Focus(val point: GeoPoint, val zoom: Double = MapFit.SINGLE_PLACE_ZOOM) : MapCamera

    /** Leaves the map where the user has put it. */
    data object Keep : MapCamera
}

/** A rectangle on the normalized map, from its top-left to its bottom-right corner. */
data class NormalizedBounds(val left: Double, val top: Double, val right: Double, val bottom: Double)

/** How [MapCamera.FitAll] shows a set of points. */
sealed interface FitPlan {
    /** The whole world, for no points at all. */
    data object World : FitPlan

    /** One spot, or points at the same spot: zoomed in on it. */
    data class Spot(val point: GeoPoint) : FitPlan

    /** The bounding box of several points. */
    data class Area(val bounds: NormalizedBounds) : FitPlan
}

/** Pure helpers that decide where the map shows its places. */
object MapFit {
    /** The zoom of a map showing one place: close enough to see the street. */
    const val SINGLE_PLACE_ZOOM = 17.0

    /** Points closer than this, in normalized units, count as one spot. Roughly a metre at the equator. */
    private const val SAME_SPOT = 1e-8

    fun plan(points: List<GeoPoint>): FitPlan {
        if (points.isEmpty()) return FitPlan.World
        val xs = points.map { WebMercator.x(it.longitude) }
        val ys = points.map { WebMercator.y(it.latitude) }
        val bounds = NormalizedBounds(xs.min(), ys.min(), xs.max(), ys.max())
        val isSpot = bounds.right - bounds.left < SAME_SPOT && bounds.bottom - bounds.top < SAME_SPOT
        return if (isSpot) FitPlan.Spot(points.first()) else FitPlan.Area(bounds)
    }
}
