package io.github.danyk20.cardholder.feature.cardlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.ui.CardSummary
import io.github.danyk20.cardholder.core.ui.LogoBadge
import io.github.danyk20.cardholder.core.ui.icon
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Lets the user rearrange all cards. Cards are shown as compact rows, which are much easier to drag
 * than full card faces; screen-reader users get "Move up" / "Move down" actions instead of dragging.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReorderScreen(cards: List<CardSummary>, actions: SortActions) {
    val listState = rememberLazyListState()
    val currentCards by rememberUpdatedState(cards)
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        // List indices include the hint row, so map the dragged items by key to positions among the cards.
        val fromIndex = currentCards.indexOfFirst { it.card.id.value == from.key }
        val toIndex = currentCards.indexOfFirst { it.card.id.value == to.key }
        if (fromIndex >= 0 && toIndex >= 0) actions.onMove(fromIndex, toIndex)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cardlist_reorder)) },
                navigationIcon = {
                    IconButton(onClick = actions.onReorderCancel) {
                        Icon(
                            CardholderIcons.Close,
                            contentDescription = stringResource(R.string.cardlist_reorder_cancel),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = actions.onReorderDone) { Text(stringResource(R.string.cardlist_reorder_done)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item(key = "hint") {
                Text(
                    stringResource(R.string.cardlist_reorder_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(cards, key = { it.card.id.value }) { summary ->
                ReorderableItem(reorderState, key = summary.card.id.value) { isDragging ->
                    ReorderRow(summary, isDragging, actions, this)
                }
            }
        }
    }
}

@Composable
private fun ReorderRow(
    summary: CardSummary,
    isDragging: Boolean,
    actions: SortActions,
    scope: ReorderableCollectionItemScope,
) {
    val card = summary.card
    val moveUp = stringResource(R.string.cardlist_move_up)
    val moveDown = stringResource(R.string.cardlist_move_down)
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = if (isDragging) 8.dp else 1.dp,
        shadowElevation = if (isDragging) 8.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(moveUp) { actions.onMoveUp(card.id).let { true } },
                    CustomAccessibilityAction(moveDown) { actions.onMoveDown(card.id).let { true } },
                )
            },
    ) {
        ListItem(
            leadingContent = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = 64.dp, height = 40.dp)
                        .background(summary.faceColors.brush, RoundedCornerShape(6.dp)),
                ) {
                    val logo = card.logo
                    if (logo != null) {
                        LogoBadge(model = logo, contentDescription = null, height = 28.dp)
                    } else {
                        Icon(card.type.icon, contentDescription = null, tint = summary.faceColors.content)
                    }
                }
            },
            headlineContent = { Text(card.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                if (summary.subtitle !=
                    card.title
                ) {
                    Text(summary.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            trailingContent = {
                Row {
                    with(scope) {
                        IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                            Icon(
                                CardholderIcons.DragHandle,
                                contentDescription = stringResource(R.string.cardlist_drag_handle, card.title),
                            )
                        }
                    }
                }
            },
        )
    }
}
