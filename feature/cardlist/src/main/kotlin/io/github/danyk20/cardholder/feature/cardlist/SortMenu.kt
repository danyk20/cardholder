package io.github.danyk20.cardholder.feature.cardlist

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CardSort

/** Sort and reorder callbacks of the card list. */
internal class SortActions(
    val onSortChange: (CardSort) -> Unit,
    val onStartReorder: () -> Unit,
    val onMove: (from: Int, to: Int) -> Unit,
    val onMoveUp: (CardSummaryKey) -> Unit,
    val onMoveDown: (CardSummaryKey) -> Unit,
    val onReorderDone: () -> Unit,
    val onReorderCancel: () -> Unit,
)

/** Identifies a card in the reorder list. */
internal typealias CardSummaryKey = CardId

@Composable
internal fun SortMenu(sort: CardSort, actions: SortActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(CardholderIcons.Sort, contentDescription = stringResource(R.string.cardlist_sort))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CardSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    leadingIcon = { RadioButton(selected = option == sort, onClick = null) },
                    onClick = {
                        expanded = false
                        actions.onSortChange(option)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.cardlist_reorder)) },
                leadingIcon = { Icon(CardholderIcons.Reorder, contentDescription = null) },
                onClick = {
                    expanded = false
                    actions.onStartReorder()
                },
            )
        }
    }
}

private val CardSort.label: Int
    get() = when (this) {
        CardSort.NAME -> R.string.cardlist_sort_name
        CardSort.DATE_ADDED -> R.string.cardlist_sort_date
        CardSort.CUSTOM -> R.string.cardlist_sort_custom
    }
