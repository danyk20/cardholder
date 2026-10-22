package io.github.danyk20.cardholder.feature.cardplaces

import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.domain.location.Place
import io.github.danyk20.cardholder.core.map.MapCamera
import io.github.danyk20.cardholder.core.map.MapMarker
import java.time.Instant

/** A place as the list shows it. [number] is the number its marker shows. */
data class PlaceItem(
    val id: Long,
    val number: Int,
    /** Where the place is on the map; `null` for a place known only from its Wi‑Fi networks. */
    val point: GeoPoint?,
    val accuracyMeters: Float?,
    val isPinned: Boolean,
    val fixCount: Int,
    val useCount: Int,
    val lastUsedAt: Instant,
    val wifiNames: List<String>,
)

/** What the user is doing on the screen. */
sealed interface PlacesMode {
    /** Looking at the places; the map and list respond to taps as selection. */
    data object Browse : PlacesMode

    /** Moving the place [placeId] to a spot tapped on the map, which is [pending] until saved. */
    data class Move(val placeId: Long, val pending: GeoPoint?) : PlacesMode

    /** Adding a place at a spot tapped on the map, which is [pending] until saved. */
    data class Add(val pending: GeoPoint?) : PlacesMode
}

/** Short confirmations shown in the snackbar. */
enum class PlacesMessage { MOVED, ADDED, DELETED, FORGOTTEN, NOT_SAVED }

/** One-shot things the screen does: show a message, or close because the card is gone. */
sealed interface CardPlacesEvent {
    data class Show(val message: PlacesMessage) : CardPlacesEvent

    /** The card was deleted while the screen was open. */
    data object Close : CardPlacesEvent
}

data class CardPlacesUiState(
    val title: String = "",
    val places: List<PlaceItem> = emptyList(),
    val selectedId: Long? = null,
    val mode: PlacesMode = PlacesMode.Browse,
    val camera: MapCamera = MapCamera.FitAll,
    /** Whether suggestions by location are on for this card; without them its places aren't used. */
    val suggestionsOn: Boolean = false,
    /** Whether "Centre on me" can work, i.e. some location permission is granted. */
    val canCentreOnMe: Boolean = false,
    val tilesUnreachable: Boolean = false,
    /** The place whose deletion awaits confirmation. */
    val pendingDelete: Long? = null,
    val confirmForgetAll: Boolean = false,
) {
    /** The places on the map: the one being moved shows its old spot faded. */
    val markers: List<MapMarker>
        get() = places.mapNotNull { item ->
            item.point?.let { point ->
                MapMarker(
                    id = item.id,
                    point = point,
                    accuracyMeters = item.accuracyMeters,
                    number = item.number,
                    isSelected = item.id == selectedId,
                    isPinned = item.isPinned,
                    isFaded = (mode as? PlacesMode.Move)?.placeId == item.id,
                )
            }
        }

    /** The pending pin of a move or an addition, if the user has placed one. */
    val editPin: GeoPoint?
        get() = when (val current = mode) {
            PlacesMode.Browse -> null
            is PlacesMode.Move -> current.pending
            is PlacesMode.Add -> current.pending
        }
}

/** Turns the card's places into list items; numbers follow the order of [places], the most used first. */
internal fun List<Place>.toPlaceItems(): List<PlaceItem> = mapIndexed { index, place ->
    PlaceItem(
        id = place.id,
        number = index + 1,
        point = place.centre,
        accuracyMeters = place.accuracyMeters,
        isPinned = place.isPinned,
        fixCount = place.fixCount,
        useCount = place.useCount,
        lastUsedAt = place.lastUsedAt,
        wifiNames = place.accessPoints.mapNotNull { it.ssid?.takeIf(String::isNotBlank) }.distinct(),
    )
}
