package io.github.danyk20.cardholder.feature.cardlist

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.designsystem.theme.CardholderTheme
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.ui.CardFace
import io.github.danyk20.cardholder.core.ui.icon
import io.github.danyk20.cardholder.core.ui.label

@Composable
fun CardListRoute(
    onCardClick: (Card) -> Unit,
    onCardLongClick: (Card) -> Unit,
    onAddCard: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: CardListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CardListScreen(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onTypeToggled = viewModel::onTypeToggled,
        onCardClick = onCardClick,
        onCardLongClick = onCardLongClick,
        onAddCard = onAddCard,
        onOpenSettings = onOpenSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardListScreen(
    uiState: CardListUiState,
    onQueryChange: (String) -> Unit,
    onTypeToggled: (CardType) -> Unit,
    onCardClick: (Card) -> Unit,
    onCardLongClick: (Card) -> Unit,
    onAddCard: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cardlist_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(CardholderIcons.Settings, contentDescription = stringResource(R.string.cardlist_settings))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddCard,
                icon = { Icon(CardholderIcons.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.cardlist_add_card)) },
            )
        },
    ) { padding ->
        when (uiState) {
            CardListUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }

            is CardListUiState.Success -> if (uiState.hasAnyCards) {
                CardGrid(
                    state = uiState,
                    contentPadding = padding,
                    onQueryChange = onQueryChange,
                    onTypeToggled = onTypeToggled,
                    onCardClick = onCardClick,
                    onCardLongClick = onCardLongClick,
                )
            } else {
                EmptyState(Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun CardGrid(
    state: CardListUiState.Success,
    contentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onTypeToggled: (CardType) -> Unit,
    onCardClick: (Card) -> Unit,
    onCardLongClick: (Card) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SearchAndFilters(state.query, state.visibleTypes, onQueryChange, onTypeToggled)
        }
        if (state.cards.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.cardlist_no_results),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        }
        items(state.cards, key = { it.summary.card.id.value }) { item ->
            val card = item.summary.card
            if (card.type == CardType.LOYALTY) {
                LoyaltyCardFace(
                    summary = item.summary,
                    code = item.code,
                    onClick = { onCardClick(card) },
                    onLongClick = { onCardLongClick(card) },
                    modifier = Modifier.animateItem(),
                )
            } else {
                CardFace(
                    summary = item.summary,
                    onClick = { onCardClick(card) },
                    onLongClick = { onCardLongClick(card) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun SearchAndFilters(
    query: String,
    visibleTypes: Set<CardType>,
    onQueryChange: (String) -> Unit,
    onTypeToggled: (CardType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.cardlist_search)) },
            leadingIcon = { Icon(CardholderIcons.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(CardholderIcons.Close, contentDescription = stringResource(R.string.cardlist_clear_search))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            CardType.entries.forEach { type ->
                FilterChip(
                    selected = type in visibleTypes,
                    onClick = { onTypeToggled(type) },
                    label = { Text(stringResource(type.label)) },
                    leadingIcon = {
                        Icon(
                            if (type in visibleTypes) CardholderIcons.Check else type.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Icon(
            CardholderIcons.Empty,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Text(
            text = stringResource(R.string.cardlist_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.cardlist_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Preview
@Composable
private fun EmptyCardListPreview() {
    CardholderTheme {
        CardListScreen(
            uiState = CardListUiState.Success(emptyList(), "", CardType.entries.toSet(), hasAnyCards = false),
            onQueryChange = {},
            onTypeToggled = {},
            onCardClick = {},
            onCardLongClick = {},
            onAddCard = {},
            onOpenSettings = {},
        )
    }
}
