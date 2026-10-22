package io.github.danyk20.cardholder.core.map

import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** A place to show on the map. [number] is the number its marker shows, the same as in the list. */
data class MapMarker(
    val id: Long,
    val point: GeoPoint,
    val accuracyMeters: Float?,
    val number: Int,
    val isSelected: Boolean = false,
    val isPinned: Boolean = false,
    /** The place's old position, shown for comparison while it's being moved. */
    val isFaded: Boolean = false,
)

/** How a marker looks. */
enum class MarkerStyle {
    /** A place learned from the card's use. */
    LEARNED,

    /** A place the user placed on the map. */
    PINNED,

    /** The place the user has selected. */
    SELECTED,

    /** The old position of a place being moved. */
    FADED,

    /** The pending pin of a move or an addition. */
    EDIT,
}

/** What the map draws for one marker: where, how, and the circle of its accuracy if any. */
data class MarkerOverlay(
    val id: String,
    val x: Double,
    val y: Double,
    val style: MarkerStyle,
    val label: String?,
    /** The accuracy circle as normalized points, or `null` if the marker has no accuracy. */
    val circle: List<Pair<Double, Double>>?,
)

/** Turns places into what the map draws; pure, so the mapping is tested without a map. */
object MapOverlays {
    private const val PLACE_PREFIX = "place:"
    const val EDIT_ID = "edit"

    /** The overlay id of the place [id]. */
    fun idOf(id: Long): String = "$PLACE_PREFIX$id"

    /** The place a marker with overlay id [overlayId] stands for, or `null` for the pending pin. */
    fun placeIdOf(overlayId: String): Long? =
        if (overlayId.startsWith(PLACE_PREFIX)) overlayId.removePrefix(PLACE_PREFIX).toLongOrNull() else null

    /** The style of [marker]: the selected place stands out, then pinned, then learned. */
    fun styleOf(marker: MapMarker): MarkerStyle = when {
        marker.isFaded -> MarkerStyle.FADED
        marker.isSelected -> MarkerStyle.SELECTED
        marker.isPinned -> MarkerStyle.PINNED
        else -> MarkerStyle.LEARNED
    }

    /** One overlay per marker, then the pending pin on top if there is one. */
    fun of(markers: List<MapMarker>, editPin: GeoPoint?): List<MarkerOverlay> {
        val places = markers.map { marker ->
            val accuracy = marker.accuracyMeters?.toDouble()
            MarkerOverlay(
                id = idOf(marker.id),
                x = WebMercator.x(marker.point.longitude),
                y = WebMercator.y(marker.point.latitude),
                style = styleOf(marker),
                label = marker.number.toString(),
                circle = accuracy?.let { circleOf(marker.point, it) },
            )
        }
        val pending = editPin?.let { point ->
            MarkerOverlay(
                id = EDIT_ID,
                x = WebMercator.x(point.longitude),
                y = WebMercator.y(point.latitude),
                style = MarkerStyle.EDIT,
                label = null,
                circle = null,
            )
        }
        return places + listOfNotNull(pending)
    }

    /**
     * The circle of [radiusMeters] around [centre] as normalized points, following the Earth's surface
     * so it's a true circle on the ground (it's stretched on the projected map at high latitudes, as
     * the map is). Non-positive radii give no circle.
     */
    fun circleOf(centre: GeoPoint, radiusMeters: Double, segments: Int = CIRCLE_SEGMENTS): List<Pair<Double, Double>>? {
        if (!(radiusMeters > 0.0)) return null
        val angularDistance = radiusMeters / EARTH_RADIUS_METERS
        val lat1 = Math.toRadians(centre.latitude)
        val lon1 = Math.toRadians(centre.longitude)
        return (0 until segments).map { step ->
            val bearing = 2 * Math.PI * step / segments
            val lat2 = asin(sin(lat1) * cos(angularDistance) + cos(lat1) * sin(angularDistance) * cos(bearing))
            val lon2 = lon1 + atan2(
                sin(bearing) * sin(angularDistance) * cos(lat1),
                cos(angularDistance) - sin(lat1) * sin(lat2),
            )
            WebMercator.x(wrapLongitude(Math.toDegrees(lon2))) to WebMercator.y(Math.toDegrees(lat2))
        }
    }

    /** Brings a longitude into -180..180 degrees. */
    private fun wrapLongitude(degrees: Double): Double = ((degrees + WRAP_OFFSET) % FULL_TURN) - HALF_TURN

    private const val EARTH_RADIUS_METERS = 6_371_008.8
    private const val CIRCLE_SEGMENTS = 48
    private const val FULL_TURN = 360.0
    private const val HALF_TURN = 180.0

    /** Shifts a longitude into a positive range before the modulo, so the result is always in -180..180. */
    private const val WRAP_OFFSET = 540.0
}
