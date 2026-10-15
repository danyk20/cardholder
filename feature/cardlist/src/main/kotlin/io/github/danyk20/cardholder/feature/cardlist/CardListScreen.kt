package io.github.danyk20.cardholder.feature.cardlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.danyk20.cardholder.core.designsystem.component.CardShape
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.designsystem.theme.CardholderTheme
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.ui.AuthenticationResult
import io.github.danyk20.cardholder.core.ui.CardFace
import io.github.danyk20.cardholder.core.ui.R as UiR
import io.github.danyk20.cardholder.core.ui.icon
import io.github.danyk20.cardholder.core.ui.label
import io.github.danyk20.cardholder.core.ui.rememberAuthenticator
import io.github.danyk20.cardholder.core.ui.sharedCardBounds

@Composable
fun CardListRoute(
    onCardClick: (Card) -> Unit,
    onAddCard: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: CardListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sortActions = remember(viewModel) {
        SortActions(
            onSortChange = viewModel::onSortChange,
            onStartReorder = viewModel::onStartReorder,
            onMove = viewModel::onMove,
            onMoveUp = { viewModel.onMoveBy(it, -1) },
            onMoveDown = { viewModel.onMoveBy(it, 1) },
            onReorderDone = viewModel::onReorderDone,
            onReorderCancel = viewModel::onReorderCancel,
        )
    }
    val reordering = (uiState as? CardListUiState.Success)?.reordering
    if (reordering != null) {
        BackHandler(onBack = viewModel::onReorderCancel)
        ReorderScreen(cards = reordering, actions = sortActions)
        return
    }
    val selected = (uiState as? CardListUiState.Success)?.selected.orEmpty()
    val selecting = selected.isNotEmpty()
    if (selecting) BackHandler(onBack = viewModel::onClearSelection)

    val context = LocalContext.current
    val authenticator = rememberAuthenticator()
    val authTitle = stringResource(R.string.cardlist_share_auth_title)
    var shareStep by remember { mutableStateOf<ShareStep?>(null) }
    val share = (uiState as? CardListUiState.Success)?.share ?: ShareState.Idle
    val snackbarHostState = remember { SnackbarHostState() }
    val failedMessage = stringResource(R.string.cardlist_share_failed)
    val authFailedMessage = stringResource(R.string.cardlist_share_auth_failed)
    LaunchedEffect(share) {
        when (share) {
            is ShareState.Ready -> {
                viewModel.onShareHandled()
                context.launchShareSheet(share.uri, share.cardCount)
            }

            ShareState.Failed, ShareState.AuthenticationFailed -> {
                viewModel.onShareHandled()
                snackbarHostState.showSnackbar(
                    if (share == ShareState.Failed) failedMessage else authFailedMessage,
                )
            }

            ShareState.Idle, ShareState.InProgress -> Unit
        }
    }

    CardListScreen(
        uiState = uiState,
        sortActions = sortActions,
        onQueryChange = viewModel::onQueryChange,
        onTypeToggled = viewModel::onTypeToggled,
        // While selecting, a tap adds or removes the card instead of opening it.
        onCardClick = { card -> if (selecting) viewModel.onToggleSelection(card.id) else onCardClick(card) },
        onCardLongClick = { card -> viewModel.onToggleSelection(card.id) },
        onAddCard = onAddCard,
        onOpenSettings = onOpenSettings,
        onClearSelection = viewModel::onClearSelection,
        onShare = {
            shareStep = if (selected.any { it.type == CardType.BANK }) ShareStep.BANK_WARNING else ShareStep.PASSWORD
        },
        snackbarHostState = snackbarHostState,
    )

    when (shareStep) {
        ShareStep.BANK_WARNING -> BankCardWarningDialog(
            bankCards = selected.filter { it.type == CardType.BANK },
            onConfirm = { shareStep = ShareStep.PASSWORD },
            onDismiss = { shareStep = null },
        )

        ShareStep.PASSWORD -> SharePasswordDialog(
            onDismiss = { shareStep = null },
            onConfirm = { password ->
                shareStep = null
                // CVVs and locked cards are only ever revealed after an explicit prompt.
                if (selected.any { it.isLocked || it.hasCvv }) {
                    authenticator.authenticate(authTitle) { result ->
                        if (result == AuthenticationResult.SUCCEEDED) {
                            viewModel.onShare(password)
                        } else {
                            password?.fill(' ')
                        }
                    }
                } else {
                    viewModel.onShare(password)
                }
            },
        )

        null -> Unit
    }
}

private enum class ShareStep { BANK_WARNING, PASSWORD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardListScreen(
    uiState: CardListUiState,
    sortActions: SortActions,
    onQueryChange: (String) -> Unit,
    onTypeToggled: (CardType) -> Unit,
    onCardClick: (Card) -> Unit,
    onCardLongClick: (Card) -> Unit,
    onAddCard: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearSelection: () -> Unit = {},
    onShare: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val selected = (uiState as? CardListUiState.Success)?.selected.orEmpty()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val gridState = rememberLazyGridState()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selected.isNotEmpty()) {
                SelectionTopBar(
                    count = selected.size,
                    sharing = (uiState as? CardListUiState.Success)?.share == ShareState.InProgress,
                    onClearSelection = onClearSelection,
                    onShare = onShare,
                    scrollBehavior = scrollBehavior,
                )
            } else {
                MainTopBar(uiState, sortActions, onOpenSettings, scrollBehavior)
            }
        },
        floatingActionButton = {
            if (selected.isEmpty()) AddCardButton(onAddCard, expanded = !gridState.canScrollBackward)
        },
    ) { padding ->
        when (uiState) {
            CardListUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }

            CardListUiState.Error -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(stringResource(UiR.string.error_unexpected), Modifier.padding(24.dp), textAlign = TextAlign.Center)
            }

            is CardListUiState.Success -> if (uiState.hasAnyCards) {
                CardGrid(
                    state = uiState,
                    gridState = gridState,
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
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onTypeToggled: (CardType) -> Unit,
    onCardClick: (Card) -> Unit,
    onCardLongClick: (Card) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        state = gridState,
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
            SelectableCard(
                selected = state.selected.any { it.id == card.id },
                modifier = Modifier.animateItem(),
            ) {
                CardFaceFor(item, onCardClick, onCardLongClick)
            }
        }
    }
}

@Composable
private fun CardFaceFor(item: CardListItem, onCardClick: (Card) -> Unit, onCardLongClick: (Card) -> Unit) {
    val card = item.summary.card
    if (card.type == CardType.LOYALTY) {
        LoyaltyCardFace(
            summary = item.summary,
            code = item.code,
            onClick = { onCardClick(card) },
            onLongClick = { onCardLongClick(card) },
            modifier = Modifier.sharedCardBounds(card.id),
        )
    } else {
        CardFace(
            summary = item.summary,
            onClick = { onCardClick(card) },
            onLongClick = { onCardLongClick(card) },
            modifier = Modifier.sharedCardBounds(card.id),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
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

@Composable
private fun AddCardButton(onAddCard: () -> Unit, expanded: Boolean) {
    // Shrinks to an icon while scrolled, so it doesn't cover the codes on the cards.
    ExtendedFloatingActionButton(
        text = { Text(stringResource(R.string.cardlist_add_card)) },
        icon = { Icon(CardholderIcons.Add, contentDescription = stringResource(R.string.cardlist_add_card)) },
        onClick = onAddCard,
        expanded = expanded,
    )
}

/** Outlines a selected card and marks it with a check, which is also announced to screen readers. */
@Composable
private fun SelectableCard(selected: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val selectedLabel = stringResource(R.string.cardlist_selected)
    Box(
        modifier = modifier.semantics { if (selected) stateDescription = selectedLabel },
    ) {
        content()
        if (selected) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(3.dp, MaterialTheme.colorScheme.primary, CardShape),
            )
            Icon(
                CardholderIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(4.dp)
                    .size(20.dp),
            )
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
            sortActions = SortActions({}, {}, { _, _ -> }, {}, {}, {}, {}),
            onQueryChange = {},
            onTypeToggled = {},
            onCardClick = {},
            onCardLongClick = {},
            onAddCard = {},
            onOpenSettings = {},
        )
    }
}
