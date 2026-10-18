package io.github.danyk20.cardholder.feature.cardlist

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons

/** The card list's title bar: sorting and settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainTopBar(
    uiState: CardListUiState,
    sortActions: SortActions,
    onOpenSettings: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.cardlist_title)) },
        actions = {
            if (uiState is CardListUiState.Success && uiState.hasAnyCards) {
                SortMenu(sort = uiState.sort, actions = sortActions)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(CardholderIcons.Settings, contentDescription = stringResource(R.string.cardlist_settings))
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

/** Replaces [MainTopBar] while cards are selected: how many, clear selection and share. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionTopBar(
    count: Int,
    sharing: Boolean,
    onClearSelection: () -> Unit,
    onShare: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = { Text(pluralStringResource(R.plurals.cardlist_selected_count, count, count)) },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(CardholderIcons.Close, contentDescription = stringResource(R.string.cardlist_selection_clear))
            }
        },
        actions = {
            if (sharing) {
                CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onShare) {
                    Icon(CardholderIcons.Share, contentDescription = stringResource(R.string.cardlist_share))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        scrollBehavior = scrollBehavior,
    )
}
