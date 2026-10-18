package io.github.danyk20.cardholder.feature.carddetail.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.domain.usecase.RememberResult
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.ui.secureDialogProperties
import io.github.danyk20.cardholder.feature.carddetail.PlacesUiState
import io.github.danyk20.cardholder.feature.carddetail.R

/** What the user can do with a card's suggestions by location. */
class PlaceActions(
    val onSuggestChange: (Boolean) -> Unit = {},
    val onRememberPlace: () -> Unit = {},
    val onForgetPlaces: () -> Unit = {},
)

/**
 * The card's switch for suggestions by location, how many places it learned and, for cards not
 * shown at a till in the app (bank and ID cards), a button to remember the current place.
 */
@Composable
internal fun PlacesSection(
    places: PlacesUiState,
    cardType: CardType,
    suggestByLocation: Boolean,
    actions: PlaceActions,
    modifier: Modifier = Modifier,
) {
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = suggestByLocation, role = Role.Switch, onValueChange = actions.onSuggestChange)
                .padding(vertical = 8.dp),
        ) {
            Icon(
                if (suggestByLocation) CardholderIcons.Place else CardholderIcons.PlaceOff,
                contentDescription = null,
                modifier = Modifier.padding(end = 16.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.detail_suggest_by_location), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = when {
                        places.placeCount > 0 -> stringResource(R.string.detail_learned_places, places.placeCount)
                        cardType == CardType.LOYALTY -> stringResource(R.string.detail_places_learned_at_till)
                        else -> stringResource(R.string.detail_places_learned_on_request)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = suggestByLocation, onCheckedChange = null)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (suggestByLocation && cardType != CardType.LOYALTY) {
                OutlinedButton(onClick = actions.onRememberPlace, enabled = !places.isRemembering) {
                    if (places.isRemembering) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(CardholderIcons.RememberPlace, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(stringResource(R.string.detail_remember_place), Modifier.padding(start = 8.dp))
                }
            }
            if (places.placeCount > 0) {
                TextButton(onClick = { confirmForget = true }) { Text(stringResource(R.string.detail_forget_places)) }
            }
        }
    }
    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text(stringResource(R.string.detail_forget_places_title)) },
            text = { Text(stringResource(R.string.detail_forget_places_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    actions.onForgetPlaces()
                }) { Text(stringResource(R.string.detail_forget)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) { Text(stringResource(R.string.detail_cancel)) }
            },
            properties = secureDialogProperties(),
        )
    }
}

/** Shows what became of "Remember this place" in the snackbar, then reports it as shown. */
@Composable
internal fun RememberResultMessage(result: RememberResult?, snackbarHostState: SnackbarHostState, onShown: () -> Unit) {
    val message = result?.let { messageFor(it) }
    LaunchedEffect(result) {
        result ?: return@LaunchedEffect
        message?.let { snackbarHostState.showSnackbar(it) }
        onShown()
    }
}

@Composable
private fun messageFor(result: RememberResult): String? = when (result) {
    RememberResult.REMEMBERED -> stringResource(R.string.detail_place_remembered)
    RememberResult.NOTHING_FOUND -> stringResource(R.string.detail_place_not_found)
    RememberResult.NO_PERMISSION -> stringResource(R.string.detail_place_no_permission)
    RememberResult.LOCATION_OFF -> stringResource(R.string.detail_place_location_off)
    RememberResult.SWITCHED_OFF -> null
}
