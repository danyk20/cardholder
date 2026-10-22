package io.github.danyk20.cardholder.core.map

import io.github.danyk20.cardholder.core.domain.location.GeoMath
import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/**
 * The Web Mercator projection of OpenStreetMap tiles, scaled so that the whole world spans 0..1 on
 * both axes: x grows eastwards from the antimeridian, y grows southwards from the top edge.
 */
object WebMercator {
    /** The latitude at which the projected world is square; the projection stops there. */
    const val MAX_LATITUDE = 85.0511

    /** Zoom levels of the map, from 0 (the whole world in one tile) to [MAX_ZOOM]. */
    const val MAX_ZOOM = 19

    /** The number of zoom levels, as the map state counts them. */
    const val LEVEL_COUNT = MAX_ZOOM + 1

    /** The size of a tile in pixels. */
    const val TILE_SIZE = 256

    /** Degrees of longitude around the world, and half of them from the centre to the edge. */
    private const val FULL_TURN = 360.0
    private const val HALF_TURN = 180.0

    /** The width and height of the map in pixels at [MAX_ZOOM], which is a map scale of 1.0. */
    const val FULL_SIZE = TILE_SIZE * (1 shl MAX_ZOOM)

    /** The map scale that shows [zoom]; 1.0 at [MAX_ZOOM], halved for each level out. */
    fun scaleOf(zoom: Double): Double = 2.0.pow(zoom - MAX_ZOOM)

    /** The normalized x of [longitude] in degrees: 0 at -180, 1 at 180. Longitudes are not wrapped. */
    fun x(longitude: Double): Double = (longitude + HALF_TURN) / FULL_TURN

    /**
     * The normalized y of [latitude] in degrees: 0 at the top edge, 1 at the bottom edge. Latitudes
     * beyond [MAX_LATITUDE] are clamped to it.
     */
    fun y(latitude: Double): Double {
        val radians = Math.toRadians(latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE))
        return (1 - ln(tan(radians) + 1 / cos(radians)) / PI) / 2
    }

    /** The longitude in degrees of the normalized [x]; the inverse of [x]. */
    fun longitude(x: Double): Double = x * FULL_TURN - HALF_TURN

    /** The latitude in degrees of the normalized [y]; the inverse of [y]. */
    fun latitude(y: Double): Double = Math.toDegrees(atan(sinh(PI * (1 - 2 * y))))

    /**
     * The place at the normalized map position ([x], [y]), e.g. where the user tapped: the latitude is
     * kept within the range a place may have and the longitude is brought into -180..180, so taps near
     * the poles and the date line give valid points. `null` for a position that isn't a number.
     */
    fun toGeo(x: Double, y: Double): GeoPoint? {
        val latitude = latitude(y).coerceIn(-GeoMath.MAX_LATITUDE, GeoMath.MAX_LATITUDE)
        return GeoMath.normalised(GeoPoint(latitude, longitude(x)))
    }

    /** The normalized position of [point] on the map. */
    fun toNormalized(point: GeoPoint): Pair<Double, Double> = x(point.longitude) to y(point.latitude)
}
