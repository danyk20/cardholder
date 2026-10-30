package io.github.danyk20.cardholder.feature.cardplaces

import io.github.danyk20.cardholder.core.domain.location.Bssid
import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.domain.location.Place
import io.github.danyk20.cardholder.core.domain.location.StoredAccessPoint
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
    /** Whether the place has Wi‑Fi networks to edit, removed ones included. */
    val hasWifi: Boolean = false,
)

/**
 * One Wi‑Fi network of a place: its access points broadcasting the same name, or one access point of a
 * hidden network. Removing it removes all of them.
 */
data class NetworkItem(
    /** Stable for the list: the name, or the address of a hidden network. */
    val key: String,
    /** `null` for a hidden network, shown by its [address]. */
    val name: String?,
    val address: String,
    val bssids: List<Bssid>,
    /** The strongest signal of its access points when they were last seen, in dBm. */
    val rssi: Int,
    /** On how many visits it was seen: the most of its access points. */
    val seenCount: Int,
    val lastSeenAt: Instant,
)

/** The Wi‑Fi editor of place [placeId], shown as number [number]. */
data class WifiEditorState(
    val placeId: Long,
    val number: Int,
    val networks: List<NetworkItem>,
    val removed: List<NetworkItem>,
    /** The place has no map position and no networks left, so it can't suggest the card any more. */
    val isUseless: Boolean,
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
    /** The place whose Wi‑Fi networks are being edited. */
    val wifiEditor: WifiEditorState? = null,
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
        hasWifi = place.accessPoints.isNotEmpty() || place.blockedAccessPoints.isNotEmpty(),
    )
}

/** The editor of [place]'s Wi‑Fi from its stored [accessPoints]. */
internal fun wifiEditorOf(place: PlaceItem, accessPoints: List<StoredAccessPoint>): WifiEditorState {
    val (removed, kept) = accessPoints.partition { it.isBlocked }
    val networks = kept.toNetworkItems()
    return WifiEditorState(
        placeId = place.id,
        number = place.number,
        networks = networks,
        removed = removed.toNetworkItems(),
        isUseless = place.point == null && networks.isEmpty(),
    )
}

/** Groups access points into networks by name, the most seen first. */
internal fun List<StoredAccessPoint>.toNetworkItems(): List<NetworkItem> =
    groupBy { it.accessPoint.ssid?.takeIf(String::isNotBlank) ?: it.accessPoint.bssid.toString() }
        .map { (key, group) ->
            val strongest = group.maxBy { it.accessPoint.rssi }
            NetworkItem(
                key = key,
                name = strongest.accessPoint.ssid?.takeIf(String::isNotBlank),
                address = strongest.accessPoint.bssid.toString(),
                bssids = group.map { it.accessPoint.bssid },
                rssi = strongest.accessPoint.rssi,
                seenCount = group.maxOf { it.seenCount },
                lastSeenAt = group.maxOf { it.lastSeenAt },
            )
        }
        .sortedWith(compareByDescending<NetworkItem> { it.seenCount }.thenByDescending { it.rssi })
