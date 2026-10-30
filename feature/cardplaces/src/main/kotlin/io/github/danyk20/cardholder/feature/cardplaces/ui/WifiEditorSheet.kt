package io.github.danyk20.cardholder.feature.cardplaces.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.domain.location.WifiSignals
import io.github.danyk20.cardholder.core.ui.secureSheetProperties
import io.github.danyk20.cardholder.feature.cardplaces.NetworkItem
import io.github.danyk20.cardholder.feature.cardplaces.R
import io.github.danyk20.cardholder.feature.cardplaces.WifiEditorState
import kotlinx.coroutines.launch

/**
 * The Wi‑Fi networks learned at one place. The user removes the ones that don't belong to it, e.g. a
 * neighbour's seen while shopping, and can undo or later restore them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WifiEditorSheet(
    state: WifiEditorState,
    onRemove: (NetworkItem) -> Unit,
    onRestore: (NetworkItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // The sheet covers the screen's snackbar, so it shows its own.
    val snackbarHost = remember { SnackbarHostState() }
    val undo = stringResource(R.string.places_wifi_undo)
    var showRemoved by rememberSaveable { mutableStateOf(false) }

    fun remove(network: NetworkItem, message: String) {
        onRemove(network)
        scope.launch {
            snackbarHost.currentSnackbarData?.dismiss()
            if (snackbarHost.showSnackbar(message, actionLabel = undo) == SnackbarResult.ActionPerformed) {
                onRestore(network)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, properties = secureSheetProperties()) {
        Box {
            LazyColumn(Modifier.fillMaxWidth()) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.places_wifi_title, state.number),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(R.string.places_wifi_explainer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.networks.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(
                                if (state.isUseless) R.string.places_wifi_nothing_left else R.string.places_wifi_none,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                }
                items(state.networks, key = { "network:${it.key}" }) { network ->
                    val label = labelOf(network)
                    val message = stringResource(R.string.places_wifi_removed_message, label)
                    NetworkRow(network, label) {
                        IconButton(onClick = { remove(network, message) }) {
                            Icon(
                                CardholderIcons.Delete,
                                contentDescription = stringResource(R.string.places_wifi_remove, label),
                            )
                        }
                    }
                }
                if (state.removed.isNotEmpty()) {
                    item {
                        TextButton(
                            onClick = { showRemoved = !showRemoved },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) {
                            Text(stringResource(R.string.places_wifi_removed_header, state.removed.size))
                        }
                    }
                    if (showRemoved) {
                        item {
                            Text(
                                text = stringResource(R.string.places_wifi_removed_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                        items(state.removed, key = { "removed:${it.key}" }) { network ->
                            NetworkRow(network, labelOf(network)) {
                                TextButton(onClick = { onRestore(network) }) {
                                    Text(stringResource(R.string.places_wifi_restore))
                                }
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
                        ) {
                            Text(stringResource(R.string.places_wifi_done))
                        }
                    }
                }
            }
            SnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun NetworkRow(network: NetworkItem, label: String, action: @Composable () -> Unit) {
    val signal = stringResource(
        when {
            network.rssi >= WifiSignals.STRONG_RSSI -> R.string.places_wifi_signal_strong
            network.rssi >= WifiSignals.GOOD_RSSI -> R.string.places_wifi_signal_good
            else -> R.string.places_wifi_signal_weak
        },
    )
    val strength = if (network.bssids.size > 1) {
        val count = network.bssids.size
        stringResource(
            R.string.places_learned_with_accuracy,
            signal,
            pluralStringResource(R.plurals.places_wifi_access_points, count, count),
        )
    } else {
        signal
    }
    val seen = pluralStringResource(
        R.plurals.places_wifi_seen,
        network.seenCount,
        network.seenCount,
        dateOf(network.lastSeenAt),
    )
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text("$strength\n$seen") },
        trailingContent = action,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

@Composable
private fun labelOf(network: NetworkItem): String =
    network.name ?: stringResource(R.string.places_wifi_hidden, network.address)
