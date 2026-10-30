package io.github.danyk20.cardholder.feature.cardplaces

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.map.MapCamera
import io.github.danyk20.cardholder.core.map.MapMarker
import io.github.danyk20.cardholder.core.map.PlacesMap
import io.github.danyk20.cardholder.core.ui.R as UiR
import io.github.danyk20.cardholder.core.ui.secureDialogProperties
import io.github.danyk20.cardholder.feature.cardplaces.ui.PlaceRow
import io.github.danyk20.cardholder.feature.cardplaces.ui.WifiEditorSheet
import kotlinx.coroutines.flow.Flow

/**
 * The map of the places, given to the screen as a slot so tests can supply a fake without a map or the
 * network. The default is the real map, see [DefaultPlacesMap].
 */
typealias PlacesMapSlot = @Composable (
    markers: List<MapMarker>,
    editPin: GeoPoint?,
    camera: MapCamera,
    onMapTap: (GeoPoint) -> Unit,
    onMarkerTap: (Long) -> Unit,
    onTilesUnreachable: (Boolean) -> Unit,
) -> Unit

/** The real map with OpenStreetMap tiles. */
val DefaultPlacesMap: PlacesMapSlot = { markers, editPin, camera, onMapTap, onMarkerTap, onTilesUnreachable ->
    PlacesMap(
        markers = markers,
        editPin = editPin,
        camera = camera,
        onMapTap = onMapTap,
        onMarkerTap = onMarkerTap,
        onTilesUnreachable = onTilesUnreachable,
    )
}

/** What the user does on the screen, as functions the view-model handles. */
class CardPlacesActions(
    val onPlaceSelected: (Long) -> Unit = {},
    val onMapTap: (GeoPoint) -> Unit = {},
    val onStartMove: (Long) -> Unit = {},
    val onStartAdd: () -> Unit = {},
    val onCancelEdit: () -> Unit = {},
    val onSaveEdit: () -> Unit = {},
    val onRequestDelete: (Long) -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onOpenWifi: (Long) -> Unit = {},
    val onCloseWifi: () -> Unit = {},
    val onRemoveNetwork: (NetworkItem) -> Unit = {},
    val onRestoreNetwork: (NetworkItem) -> Unit = {},
    val onRequestForgetAll: () -> Unit = {},
    val onCancelForgetAll: () -> Unit = {},
    val onConfirmForgetAll: () -> Unit = {},
    val onCentreOnMe: () -> Unit = {},
    val onTilesUnreachable: (Boolean) -> Unit = {},
)

/** The places of one card on a map and in a list. */
@Composable
fun CardPlacesRoute(onBack: () -> Unit, viewModel: CardPlacesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        CardPlacesActions(
            onPlaceSelected = viewModel::onPlaceSelected,
            onMapTap = viewModel::onMapTap,
            onStartMove = viewModel::onStartMove,
            onStartAdd = viewModel::onStartAdd,
            onCancelEdit = viewModel::onCancelEdit,
            onSaveEdit = viewModel::onSaveEdit,
            onRequestDelete = viewModel::onRequestDelete,
            onCancelDelete = viewModel::onCancelDelete,
            onConfirmDelete = viewModel::onConfirmDelete,
            onOpenWifi = viewModel::onOpenWifi,
            onCloseWifi = viewModel::onCloseWifi,
            onRemoveNetwork = viewModel::onRemoveNetwork,
            onRestoreNetwork = viewModel::onRestoreNetwork,
            onRequestForgetAll = viewModel::onRequestForgetAll,
            onCancelForgetAll = viewModel::onCancelForgetAll,
            onConfirmForgetAll = viewModel::onConfirmForgetAll,
            onCentreOnMe = viewModel::onCentreOnMe,
            onTilesUnreachable = viewModel::onTilesUnreachable,
        )
    }
    CardPlacesScreen(state = state, events = viewModel.uiEvents, actions = actions, onBack = onBack)
}

/** Below this width the map and the list sit side by side, as on tablets and in landscape. */
private val WIDE_LAYOUT = 600.dp

/** The share of the height the map takes on phones. */
private const val MAP_SHARE = 0.55f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardPlacesScreen(
    state: CardPlacesUiState,
    events: Flow<CardPlacesEvent>,
    actions: CardPlacesActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    map: PlacesMapSlot = DefaultPlacesMap,
) {
    val snackbarHost = remember { SnackbarHostState() }
    val currentOnBack by rememberUpdatedState(onBack)
    val messages = PlacesMessages(
        moved = stringResource(R.string.places_moved),
        added = stringResource(R.string.places_added),
        deleted = stringResource(R.string.places_deleted),
        forgotten = stringResource(R.string.places_forgotten),
        notSaved = stringResource(R.string.places_not_saved),
    )
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                CardPlacesEvent.Close -> currentOnBack()
                is CardPlacesEvent.Show -> snackbarHost.showSnackbar(messages.of(event.message))
            }
        }
    }
    BackHandler(enabled = state.mode != PlacesMode.Browse, onBack = actions.onCancelEdit)

    var menuOpen by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.places_title, state.title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CardholderIcons.Back, contentDescription = stringResource(UiR.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(CardholderIcons.More, contentDescription = stringResource(R.string.places_more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.places_forget_all)) },
                            onClick = {
                                menuOpen = false
                                actions.onRequestForgetAll()
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.mode == PlacesMode.Browse) {
                ExtendedFloatingActionButton(
                    onClick = actions.onStartAdd,
                    icon = { Icon(CardholderIcons.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.places_add)) },
                )
            }
        },
        bottomBar = {
            if (state.mode != PlacesMode.Browse) {
                EditBar(
                    mode = state.mode,
                    suggestionsOn = state.suggestionsOn,
                    onCancel = actions.onCancelEdit,
                    onSave = actions.onSaveEdit,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            if (maxWidth >= WIDE_LAYOUT) {
                Row(Modifier.fillMaxSize()) {
                    MapPane(state, actions, map, Modifier.weight(WIDE_MAP_SHARE))
                    PlacesPane(state, actions, Modifier.weight(1 - WIDE_MAP_SHARE))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    MapPane(state, actions, map, Modifier.weight(MAP_SHARE))
                    PlacesPane(state, actions, Modifier.weight(1 - MAP_SHARE))
                }
            }
        }
    }

    state.wifiEditor?.let { editor ->
        WifiEditorSheet(
            state = editor,
            onRemove = actions.onRemoveNetwork,
            onRestore = actions.onRestoreNetwork,
            onDismiss = actions.onCloseWifi,
        )
    }
    state.pendingDelete?.let {
        AlertDialog(
            onDismissRequest = actions.onCancelDelete,
            title = { Text(stringResource(R.string.places_delete_title)) },
            text = { Text(stringResource(R.string.places_delete_message)) },
            confirmButton = {
                TextButton(onClick = actions.onConfirmDelete) { Text(stringResource(R.string.places_delete)) }
            },
            dismissButton = {
                TextButton(onClick = actions.onCancelDelete) { Text(stringResource(UiR.string.action_cancel)) }
            },
            properties = secureDialogProperties(),
        )
    }
    if (state.confirmForgetAll) {
        AlertDialog(
            onDismissRequest = actions.onCancelForgetAll,
            title = { Text(stringResource(R.string.places_forget_all_title)) },
            text = { Text(stringResource(R.string.places_forget_all_message)) },
            confirmButton = {
                TextButton(onClick = actions.onConfirmForgetAll) { Text(stringResource(R.string.places_forget)) }
            },
            dismissButton = {
                TextButton(onClick = actions.onCancelForgetAll) { Text(stringResource(UiR.string.action_cancel)) }
            },
            properties = secureDialogProperties(),
        )
    }
}

private const val WIDE_MAP_SHARE = 0.6f

/** Room at the end of the list for the add button, which floats over it. */
private val LIST_END_SPACE = 88.dp

/** The confirmations' texts, looked up once per composition. */
private class PlacesMessages(
    val moved: String,
    val added: String,
    val deleted: String,
    val forgotten: String,
    val notSaved: String,
) {
    fun of(message: PlacesMessage): String = when (message) {
        PlacesMessage.MOVED -> moved
        PlacesMessage.ADDED -> added
        PlacesMessage.DELETED -> deleted
        PlacesMessage.FORGOTTEN -> forgotten
        PlacesMessage.NOT_SAVED -> notSaved
    }
}

@Composable
private fun MapPane(state: CardPlacesUiState, actions: CardPlacesActions, map: PlacesMapSlot, modifier: Modifier) {
    val description = pluralStringResource(R.plurals.places_map_description, state.places.size, state.places.size)
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth().semantics { contentDescription = description }) {
            map(
                state.markers,
                state.editPin,
                state.camera,
                actions.onMapTap,
                actions.onPlaceSelected,
                actions.onTilesUnreachable,
            )
            if (state.tilesUnreachable) {
                Text(
                    text = stringResource(R.string.places_map_offline),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (state.canCentreOnMe) {
                SmallFloatingActionButton(
                    onClick = actions.onCentreOnMe,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Icon(CardholderIcons.Place, contentDescription = stringResource(R.string.places_centre_on_me))
                }
            }
        }
        Text(
            text = stringResource(R.string.places_map_privacy),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PlacesPane(state: CardPlacesUiState, actions: CardPlacesActions, modifier: Modifier) {
    Column(modifier) {
        HorizontalDivider()
        if (state.places.isEmpty()) {
            Text(
                text = stringResource(R.string.places_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = LIST_END_SPACE),
        ) {
            items(state.places, key = { it.id }) { item ->
                PlaceRow(
                    item = item,
                    isSelected = state.selectedId == item.id,
                    onClick = { actions.onPlaceSelected(item.id) },
                    onMove = { actions.onStartMove(item.id) },
                    onEditWifi = { actions.onOpenWifi(item.id) },
                    onDelete = { actions.onRequestDelete(item.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EditBar(mode: PlacesMode, suggestionsOn: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    val pending = when (mode) {
        PlacesMode.Browse -> null
        is PlacesMode.Move -> mode.pending
        is PlacesMode.Add -> mode.pending
    }
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(
                    if (mode is PlacesMode.Move) R.string.places_move_hint else R.string.places_add_hint,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (mode is PlacesMode.Add && !suggestionsOn) {
                Text(
                    text = stringResource(R.string.places_suggestions_off_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text(stringResource(UiR.string.action_cancel)) }
                Button(onClick = onSave, enabled = pending != null) { Text(stringResource(R.string.places_save)) }
            }
        }
    }
}
