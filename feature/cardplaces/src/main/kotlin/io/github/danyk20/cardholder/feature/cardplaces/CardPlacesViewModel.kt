package io.github.danyk20.cardholder.feature.cardplaces

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.danyk20.cardholder.core.domain.location.FixRequest
import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.domain.location.LocationAccess
import io.github.danyk20.cardholder.core.domain.location.LocationPermission
import io.github.danyk20.cardholder.core.domain.location.SurroundingsProvider
import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.domain.usecase.CardPlaces
import io.github.danyk20.cardholder.core.map.MapCamera
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.ui.launchSafely
import io.github.danyk20.cardholder.feature.cardplaces.navigation.CardPlacesDestination
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * The places of one card: selecting them, moving, adding and deleting them, and forgetting them all.
 * A move or an addition is kept in [SavedStateHandle], so rotation doesn't lose the pending pin.
 */
@HiltViewModel
@Suppress("TooManyFunctions") // One handler per user action on the screen.
class CardPlacesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cardRepository: CardRepository,
    private val cardPlaces: CardPlaces,
    private val surroundings: SurroundingsProvider,
    private val locationAccess: LocationAccess,
) : ViewModel() {
    private val cardId = CardId(savedStateHandle.toRoute<CardPlacesDestination>().cardId)
    private val handle = savedStateHandle

    private val placeItems: StateFlow<List<PlaceItem>> = cardPlaces.observePlaces(cardId)
        .map { it.toPlaceItems() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val editing = MutableStateFlow(restoreEditing())
    private val transient = MutableStateFlow(Transient())
    private val events = Channel<CardPlacesEvent>(Channel.BUFFERED)

    /** Everything the user can see but that isn't a saved place or card. */
    private data class Transient(
        val selectedId: Long? = null,
        val camera: MapCamera = MapCamera.FitAll,
        val tilesUnreachable: Boolean = false,
        val pendingDelete: Long? = null,
        val confirmForgetAll: Boolean = false,
    )

    /** A move of [placeId], or an addition when it's `null`, with the spot tapped so far. */
    private data class Editing(val placeId: Long?, val pending: GeoPoint?)

    val uiEvents: Flow<CardPlacesEvent> = events.receiveAsFlow()

    val uiState: StateFlow<CardPlacesUiState> = combine(
        cardRepository.observeCard(cardId),
        placeItems,
        transient,
        editing,
    ) { card, places, shown, edit ->
        CardPlacesUiState(
            title = card?.title.orEmpty(),
            places = places,
            selectedId = shown.selectedId,
            mode =
                edit?.let {
                    if (it.placeId !=
                        null
                    ) {
                        PlacesMode.Move(it.placeId, it.pending)
                    } else {
                        PlacesMode.Add(it.pending)
                    }
                }
                    ?: PlacesMode.Browse,
            camera = shown.camera,
            suggestionsOn = card?.suggestByLocation == true,
            canCentreOnMe = locationAccess.permission() != LocationPermission.NONE,
            tilesUnreachable = shown.tilesUnreachable,
            pendingDelete = shown.pendingDelete,
            confirmForgetAll = shown.confirmForgetAll,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CardPlacesUiState())

    init {
        // The card can't be used any more once it's deleted, so the screen closes with it.
        launchSafely {
            cardRepository.observeCard(cardId).first { it == null }
            events.send(CardPlacesEvent.Close)
        }
    }

    fun onPlaceSelected(placeId: Long) {
        val current = transient.value
        if (current.selectedId == placeId) {
            transient.update { it.copy(selectedId = null) }
            return
        }
        val point = placeItems.value.firstOrNull { it.id == placeId }?.point
        transient.update {
            it.copy(selectedId = placeId, camera = point?.let { p -> MapCamera.Focus(p) } ?: it.camera)
        }
    }

    fun onMapTap(point: GeoPoint) {
        if (editing.value != null) {
            editing.update { it?.copy(pending = point) }
            persistEditing()
        } else {
            transient.update { it.copy(selectedId = null) }
        }
    }

    fun onStartMove(placeId: Long) {
        editing.value = Editing(placeId, pending = null)
        persistEditing()
    }

    fun onStartAdd() {
        editing.value = Editing(placeId = null, pending = null)
        persistEditing()
    }

    /** Leaves move or add mode without saving anything. */
    fun onCancelEdit() {
        editing.value = null
        persistEditing()
    }

    fun onSaveEdit() {
        val edit = editing.value ?: return
        val pin = edit.pending ?: return
        launchSafely(onError = { events.trySend(CardPlacesEvent.Show(PlacesMessage.NOT_SAVED)) }) {
            val (selected, message) = if (edit.placeId != null) {
                val moved = cardPlaces.move(edit.placeId, pin)
                edit.placeId.takeIf { moved } to if (moved) PlacesMessage.MOVED else PlacesMessage.NOT_SAVED
            } else {
                val added = cardPlaces.add(cardId, pin)
                added to if (added != null) PlacesMessage.ADDED else PlacesMessage.NOT_SAVED
            }
            editing.value = null
            persistEditing()
            transient.update { it.copy(selectedId = selected ?: it.selectedId) }
            events.send(CardPlacesEvent.Show(message))
        }
    }

    fun onRequestDelete(placeId: Long) = transient.update { it.copy(pendingDelete = placeId) }

    fun onCancelDelete() = transient.update { it.copy(pendingDelete = null) }

    fun onConfirmDelete() {
        val placeId = transient.value.pendingDelete ?: return
        transient.update { it.copy(pendingDelete = null) }
        launchSafely(onError = { events.trySend(CardPlacesEvent.Show(PlacesMessage.NOT_SAVED)) }) {
            cardPlaces.delete(placeId)
            transient.update { if (it.selectedId == placeId) it.copy(selectedId = null) else it }
            events.send(CardPlacesEvent.Show(PlacesMessage.DELETED))
        }
    }

    fun onRequestForgetAll() = transient.update { it.copy(confirmForgetAll = true) }

    fun onCancelForgetAll() = transient.update { it.copy(confirmForgetAll = false) }

    fun onConfirmForgetAll() {
        transient.update { it.copy(confirmForgetAll = false, selectedId = null) }
        launchSafely(onError = { events.trySend(CardPlacesEvent.Show(PlacesMessage.NOT_SAVED)) }) {
            cardPlaces.forget(cardId)
            events.send(CardPlacesEvent.Show(PlacesMessage.FORGOTTEN))
        }
    }

    /** Moves the map to where the user is, if location permission allows and a fix comes in time. */
    fun onCentreOnMe() {
        if (locationAccess.permission() == LocationPermission.NONE) return
        launchSafely {
            surroundings.locationFix(FixRequest.Nearby)?.let { fix ->
                transient.update { it.copy(camera = MapCamera.Focus(fix.point, zoom = CENTRE_ON_ME_ZOOM)) }
            }
        }
    }

    fun onTilesUnreachable(unreachable: Boolean) = transient.update { it.copy(tilesUnreachable = unreachable) }

    private fun restoreEditing(): Editing? {
        if (handle.get<Boolean>(KEY_EDITING) != true) return null
        val lat = handle.get<Double>(KEY_PIN_LAT)
        val lon = handle.get<Double>(KEY_PIN_LON)
        return Editing(
            placeId = handle.get<Long>(KEY_PLACE_ID),
            pending = if (lat != null && lon != null) GeoPoint(lat, lon) else null,
        )
    }

    private fun persistEditing() {
        val edit = editing.value
        handle[KEY_EDITING] = edit != null
        handle[KEY_PLACE_ID] = edit?.placeId
        handle[KEY_PIN_LAT] = edit?.pending?.latitude
        handle[KEY_PIN_LON] = edit?.pending?.longitude
    }

    private companion object {
        const val KEY_EDITING = "editing"
        const val KEY_PLACE_ID = "editingPlaceId"
        const val KEY_PIN_LAT = "pinLatitude"
        const val KEY_PIN_LON = "pinLongitude"
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val CENTRE_ON_ME_ZOOM = 16.0
    }
}
