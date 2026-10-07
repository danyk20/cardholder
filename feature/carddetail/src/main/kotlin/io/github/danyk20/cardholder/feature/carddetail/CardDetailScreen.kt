package io.github.danyk20.cardholder.feature.carddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.ui.AuthenticationResult
import io.github.danyk20.cardholder.core.ui.LockedContent
import io.github.danyk20.cardholder.core.ui.R as UiR
import io.github.danyk20.cardholder.core.ui.SecureScreen
import io.github.danyk20.cardholder.core.ui.rememberAuthenticator
import io.github.danyk20.cardholder.core.ui.secureDialogProperties
import io.github.danyk20.cardholder.feature.carddetail.ui.CardSidesView
import io.github.danyk20.cardholder.feature.carddetail.ui.DetailFields
import io.github.danyk20.cardholder.feature.carddetail.ui.FieldActions

@Composable
fun CardDetailRoute(
    onBack: () -> Unit,
    onEdit: (CardId) -> Unit,
    onShowBarcode: (CardId) -> Unit,
    viewModel: CardDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val authenticator = rememberAuthenticator()
    val cardTitle = state.summary?.card?.title.orEmpty()
    val unlockTitle = stringResource(UiR.string.auth_title_unlock_card, cardTitle)
    val cvvTitle = stringResource(UiR.string.auth_title_show_cvv)

    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    LaunchedEffect(state.pendingAuthentication) {
        val action = state.pendingAuthentication ?: return@LaunchedEffect
        val title = if (action == AuthAction.REVEAL_CVV) cvvTitle else unlockTitle
        authenticator.authenticate(title) {
            viewModel.onAuthenticationResult(
                action,
                it == AuthenticationResult.SUCCEEDED,
            )
        }
    }
    LaunchedEffect(state.isDeleted, state.notFound) {
        if (state.isDeleted || state.notFound) onBack()
    }

    CardDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = { onEdit(viewModel.cardId) },
        onShowBarcode = { onShowBarcode(viewModel.cardId) },
        onDelete = viewModel::onDelete,
        onLockedChange = viewModel::onLockedChange,
        onFavouriteChange = viewModel::onFavouriteChange,
        onUnlock = viewModel::onUnlockDetails,
        fieldActions = remember(viewModel) {
            FieldActions(
                onCopy = viewModel::onCopy,
                onRevealCvv = viewModel::onRevealCvv,
                onHideCvv = viewModel::onHideCvv,
            )
        },
        onCopiedMessageShown = viewModel::onCopiedMessageShown,
        onErrorShown = viewModel::onErrorShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardDetailScreen(
    state: CardDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onShowBarcode: () -> Unit,
    onDelete: () -> Unit,
    onLockedChange: (Boolean) -> Unit,
    onFavouriteChange: (Boolean) -> Unit,
    onUnlock: () -> Unit,
    fieldActions: FieldActions,
    onCopiedMessageShown: () -> Unit,
    onErrorShown: () -> Unit,
) {
    SecureScreen()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val copiedMessage = state.copiedLabel?.let { stringResource(R.string.detail_copied, it) }
    LaunchedEffect(copiedMessage) {
        copiedMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(copiedMessage)
        onCopiedMessageShown()
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.summary?.card?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CardholderIcons.Back, contentDescription = stringResource(UiR.string.action_back))
                    }
                },
                actions = {
                    val card = state.summary?.card ?: return@TopAppBar
                    FavouriteButton(isFavourite = card.isFavourite, onFavouriteChange = onFavouriteChange)
                    DetailMenu(onEdit = onEdit, onDelete = { confirmDelete = true })
                },
            )
        },
    ) { padding ->
        val summary = state.summary
        if (summary == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val card = summary.card
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp)
                .widthIn(max = 600.dp),
        ) {
            val unlocked = state.details is DetailsState.Loaded
            CardSidesView(summary, photosVisible = unlocked || !card.isLocked, Modifier.fillMaxWidth())
            if (card.type == CardType.LOYALTY) {
                Button(onClick = onShowBarcode, modifier = Modifier.fillMaxWidth()) {
                    Icon(CardholderIcons.Barcode, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.detail_show_code), Modifier.padding(start = 8.dp))
                }
            }
            when (val details = state.details) {
                DetailsState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                is DetailsState.Loaded -> DetailFields(card, summary.subtitle, details.details, state.cvv, fieldActions)
                DetailsState.Locked -> LockedContent(stringResource(R.string.detail_locked_details), onUnlock)
                DetailsState.KeyInvalidated -> LockedContent(stringResource(UiR.string.auth_key_invalidated), null)
                DetailsState.Failed -> LockedContent(stringResource(UiR.string.error_unexpected), null)
            }
            HorizontalDivider()
            LockSwitch(isLocked = card.isLocked, canProtect = state.canProtect, onLockedChange = onLockedChange)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_message, state.summary?.card?.title.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.detail_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.detail_cancel)) }
            },
            properties = secureDialogProperties(),
        )
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = onErrorShown,
            text = {
                Text(
                    stringResource(
                        when (error) {
                            DetailError.KEY_INVALIDATED -> UiR.string.auth_key_invalidated
                            DetailError.UNEXPECTED -> UiR.string.error_unexpected
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onErrorShown) { Text(stringResource(R.string.detail_ok)) }
            },
        )
    }
}

@Composable
private fun FavouriteButton(isFavourite: Boolean, onFavouriteChange: (Boolean) -> Unit) {
    val description = if (isFavourite) R.string.detail_remove_favourite else R.string.detail_add_favourite
    IconButton(onClick = { onFavouriteChange(!isFavourite) }) {
        Icon(
            if (isFavourite) CardholderIcons.Favourite else CardholderIcons.NotFavourite,
            contentDescription = stringResource(description),
        )
    }
}

@Composable
private fun DetailMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = onEdit) {
        Icon(CardholderIcons.Edit, contentDescription = stringResource(R.string.detail_edit))
    }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(CardholderIcons.More, contentDescription = stringResource(R.string.detail_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.detail_delete)) },
                leadingIcon = { Icon(CardholderIcons.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun LockSwitch(isLocked: Boolean, canProtect: Boolean, onLockedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = isLocked, enabled = canProtect, role = Role.Switch, onValueChange = onLockedChange)
            .padding(vertical = 8.dp),
    ) {
        Icon(
            if (isLocked) CardholderIcons.Lock else CardholderIcons.Unlock,
            contentDescription = null,
            modifier = Modifier.padding(end = 16.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.detail_lock), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(
                    when {
                        !canProtect -> R.string.detail_lock_unavailable
                        isLocked -> R.string.detail_lock_on
                        else -> R.string.detail_lock_off
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = isLocked, onCheckedChange = null, enabled = canProtect)
    }
}
