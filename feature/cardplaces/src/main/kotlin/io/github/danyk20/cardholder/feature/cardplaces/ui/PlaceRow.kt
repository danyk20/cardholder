package io.github.danyk20.cardholder.feature.cardplaces.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.feature.cardplaces.PlaceItem
import io.github.danyk20.cardholder.feature.cardplaces.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * One place in the list. Tapping it selects it, which opens its actions: move it on the map, edit its
 * Wi‑Fi networks, delete it, or, for a place without a map position, set it on the map.
 */
@Composable
internal fun PlaceRow(
    item: PlaceItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onMove: () -> Unit,
    onEditWifi: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberBadge(item.number)
            Column(Modifier.fillMaxWidth()) {
                Text(text = titleOf(item), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = usageOf(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.wifiNames.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.places_wifi, item.wifiNames.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (isSelected) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 44.dp),
            ) {
                OutlinedButton(onClick = onMove) {
                    Text(
                        stringResource(
                            if (item.point != null) R.string.places_move else R.string.places_set_on_map,
                        ),
                    )
                }
                if (item.hasWifi) {
                    OutlinedButton(onClick = onEditWifi) { Text(stringResource(R.string.places_edit_wifi)) }
                }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.places_delete)) }
            }
        }
    }
}

@Composable
private fun NumberBadge(number: Int) {
    Text(
        text = number.toString(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(top = 6.dp),
    )
}

@Composable
private fun titleOf(item: PlaceItem): String = when {
    item.point == null -> stringResource(R.string.places_wifi_only)

    item.isPinned -> stringResource(R.string.places_set_by_you)

    else -> {
        val learned = pluralStringResource(R.plurals.places_learned_from, item.fixCount, item.fixCount)
        val accuracy = item.accuracyMeters
        if (accuracy == null) {
            learned
        } else {
            stringResource(
                R.string.places_learned_with_accuracy,
                learned,
                stringResource(R.string.places_accuracy, accuracy.toInt()),
            )
        }
    }
}

@Composable
private fun usageOf(item: PlaceItem): String = pluralStringResource(
    R.plurals.places_used,
    item.useCount,
    item.useCount,
    dateOf(item.lastUsedAt),
)

internal fun dateOf(instant: Instant): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    .withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())
    .format(instant)
