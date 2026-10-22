package io.github.danyk20.cardholder.core.map

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import ovh.plrapps.mapcompose.api.BoundingBox
import ovh.plrapps.mapcompose.api.addLayer
import ovh.plrapps.mapcompose.api.addMarker
import ovh.plrapps.mapcompose.api.addPath
import ovh.plrapps.mapcompose.api.onMarkerClick
import ovh.plrapps.mapcompose.api.onTap
import ovh.plrapps.mapcompose.api.removeAllMarkers
import ovh.plrapps.mapcompose.api.removeAllPaths
import ovh.plrapps.mapcompose.api.scrollTo
import ovh.plrapps.mapcompose.api.setColorFilterProvider
import ovh.plrapps.mapcompose.api.setMapBackground
import ovh.plrapps.mapcompose.api.snapScrollTo
import ovh.plrapps.mapcompose.core.ColorFilterProvider
import ovh.plrapps.mapcompose.ui.MapUI
import ovh.plrapps.mapcompose.ui.layout.Fit
import ovh.plrapps.mapcompose.ui.state.MapState

/** The copyright notice the OpenStreetMap licence requires on every map. */
const val OSM_ATTRIBUTION = "© OpenStreetMap contributors"
private const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
private const val FIT_PADDING = 0.12f

/** Inverts the tiles' colours, so the map is dark on the dark theme. */
private val InvertedTiles = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/**
 * A map of [markers], with the pending [editPin] of a move or an addition, drawn with OpenStreetMap
 * tiles. Taps report the place tapped on the map, or the marker's place id, which is the only way the
 * map talks to the rest of the app.
 *
 * Tiles are downloaded from OpenStreetMap only while the map is shown; [onTilesUnreachable] reports when
 * a tile couldn't be fetched, e.g. offline, so the screen can say why the map is blank.
 */
@Composable
fun PlacesMap(
    markers: List<MapMarker>,
    editPin: GeoPoint?,
    camera: MapCamera,
    onMapTap: (GeoPoint) -> Unit,
    onMarkerTap: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onTilesUnreachable: (Boolean) -> Unit = {},
) {
    val tiles = tilesFor(LocalContext.current)
    val state = remember(tiles) { newMapState(tiles) }
    DisposableEffect(state) { onDispose { state.shutdown() } }

    val currentOnMapTap by rememberUpdatedState(onMapTap)
    val currentOnMarkerTap by rememberUpdatedState(onMarkerTap)
    val currentOnUnreachable by rememberUpdatedState(onTilesUnreachable)
    DisposableEffect(state) {
        state.onTap { x, y -> WebMercator.toGeo(x, y)?.let(currentOnMapTap) }
        state.onMarkerClick { id, _, _ -> MapOverlays.placeIdOf(id)?.let(currentOnMarkerTap) }
        onDispose { }
    }
    LaunchedEffect(tiles) { tiles.isUnreachable.collect { currentOnUnreachable(it) } }

    val scheme = MaterialTheme.colorScheme
    val darkTheme = isSystemInDarkTheme()
    LaunchedEffect(state, darkTheme, scheme.surfaceVariant) {
        state.setMapBackground(scheme.surfaceVariant)
        state.setColorFilterProvider(ColorFilterProvider { _, _, _ -> if (darkTheme) InvertedTiles else null })
    }

    val overlays = remember(markers, editPin) { MapOverlays.of(markers, editPin) }
    LaunchedEffect(state, overlays, scheme) {
        state.removeAllPaths()
        state.removeAllMarkers()
        overlays.forEach { overlay ->
            overlay.circle?.let { ring ->
                state.addPath(
                    id = "accuracy:${overlay.id}",
                    color = scheme.primary.copy(alpha = RING_ALPHA),
                    fillColor = scheme.primary.copy(alpha = FILL_ALPHA),
                ) { addPoints(ring) }
            }
            state.addMarker(
                id = overlay.id,
                x = overlay.x,
                y = overlay.y,
                relativeOffset = Offset(-0.5f, -0.5f),
                zIndex = if (overlay.style == MarkerStyle.EDIT) 2f else 1f,
                clickable = overlay.style != MarkerStyle.EDIT,
            ) { MarkerDot(overlay, scheme) }
        }
    }

    val fitKey = markers.map { it.id to it.point }
    LaunchedEffect(state, camera, fitKey) {
        when (camera) {
            MapCamera.FitAll -> state.fitAll(markers.map { it.point })
            is MapCamera.Focus -> state.focus(camera)
            MapCamera.Keep -> Unit
        }
    }

    val uriHandler = LocalUriHandler.current
    Box(modifier = modifier.fillMaxSize()) {
        MapUI(modifier = Modifier.fillMaxSize(), state = state)
        Text(
            text = OSM_ATTRIBUTION,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .background(scheme.surface.copy(alpha = ATTRIBUTION_ALPHA), RoundedCornerShape(4.dp))
                .clickable(role = Role.Button) { uriHandler.openUri(OSM_COPYRIGHT_URL) }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun tilesFor(context: Context): OsmTileStreamProvider = remember(context) { MapTiles.get(context) }

private fun newMapState(tiles: OsmTileStreamProvider): MapState = MapState(
    levelCount = WebMercator.LEVEL_COUNT,
    fullWidth = WebMercator.FULL_SIZE,
    fullHeight = WebMercator.FULL_SIZE,
    tileSize = WebMercator.TILE_SIZE,
    workerCount = OsmTileStreamProvider.MAX_PARALLEL_DOWNLOADS,
) {
    scroll(CENTRE, CENTRE)
    scale(0.0)
    minimumScaleMode(Fit)
    maxScale(WebMercator.scaleOf(WebMercator.MAX_ZOOM.toDouble()))
}.apply {
    addLayer(tiles)
}

/** Shows every place: the whole world without any, one spot zoomed in, or the bounds of all places. */
private suspend fun MapState.fitAll(points: List<GeoPoint>) {
    when (val plan = MapFit.plan(points)) {
        FitPlan.World -> snapScrollTo(BoundingBox(0.0, 0.0, 1.0, 1.0))

        is FitPlan.Spot -> focus(plan.point, MapFit.SINGLE_PLACE_ZOOM)

        is FitPlan.Area -> with(plan.bounds) {
            snapScrollTo(BoundingBox(left, top, right, bottom), padding = Offset(FIT_PADDING, FIT_PADDING))
        }
    }
}

private suspend fun MapState.focus(camera: MapCamera.Focus) = focus(camera.point, camera.zoom)

private suspend fun MapState.focus(point: GeoPoint, zoom: Double) {
    val (x, y) = WebMercator.toNormalized(point)
    scrollTo(x, y, WebMercator.scaleOf(zoom))
}

@Composable
private fun MarkerDot(overlay: MarkerOverlay, scheme: ColorScheme) {
    val (background, content, diameter) = when (overlay.style) {
        MarkerStyle.LEARNED -> Triple(scheme.primary, scheme.onPrimary, 26.dp)
        MarkerStyle.PINNED -> Triple(scheme.tertiary, scheme.onTertiary, 26.dp)
        MarkerStyle.SELECTED -> Triple(scheme.secondary, scheme.onSecondary, 36.dp)
        MarkerStyle.FADED -> Triple(scheme.primary.copy(alpha = FADED_ALPHA), scheme.onPrimary, 26.dp)
        MarkerStyle.EDIT -> Triple(scheme.surface, scheme.primary, 22.dp)
    }
    val isSelected = overlay.style == MarkerStyle.SELECTED
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(diameter)
            .background(background, CircleShape)
            .then(if (isSelected) Modifier.border(2.dp, scheme.onSurface, CircleShape) else Modifier),
    ) {
        overlay.label?.let { label ->
            Text(
                text = label,
                color = content,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val CENTRE = 0.5
private const val RING_ALPHA = 0.8f
private const val FILL_ALPHA = 0.12f
private const val FADED_ALPHA = 0.35f
private const val ATTRIBUTION_ALPHA = 0.8f
