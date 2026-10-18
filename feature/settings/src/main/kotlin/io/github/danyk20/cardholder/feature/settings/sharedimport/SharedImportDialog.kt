package io.github.danyk20.cardholder.feature.settings.sharedimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.danyk20.cardholder.core.domain.model.BackupResult
import io.github.danyk20.cardholder.core.domain.model.SharedCardAction
import io.github.danyk20.cardholder.core.ui.icon
import io.github.danyk20.cardholder.core.ui.secureDialogProperties
import io.github.danyk20.cardholder.feature.settings.R
import io.github.danyk20.cardholder.feature.settings.resultMessage

/** Shown on top of the app when it was opened with a shared or backup file at [uri]. */
@Composable
fun SharedImportDialog(uri: String, onClose: () -> Unit, viewModel: SharedImportViewModel = hiltViewModel()) {
    LaunchedEffect(uri) { viewModel.open(uri) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        SharedImportUiState.Loading -> LoadingDialog()

        is SharedImportUiState.PasswordNeeded -> PasswordDialog(
            wrongPassword = current.wrongPassword,
            onConfirm = viewModel::onPassword,
            onDismiss = viewModel::onCancel,
        )

        is SharedImportUiState.Choosing -> ChooseCardsDialog(
            cards = current.cards,
            fromNewerVersion = current.fromNewerVersion,
            onActionChange = viewModel::onActionChange,
            onLockedChange = viewModel::onLockedChange,
            onImport = viewModel::onImport,
            onDismiss = viewModel::onCancel,
        )

        is SharedImportUiState.Done -> AlertDialog(
            onDismissRequest = viewModel::onCancel,
            title = { Text(stringResource(R.string.shared_import_title)) },
            text = {
                val result = current.result
                Text(
                    if (result is BackupResult.Imported && result.imported == 0) {
                        stringResource(R.string.shared_import_nothing)
                    } else {
                        resultMessage(result)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::onCancel) { Text(stringResource(R.string.shared_import_ok)) }
            },
        )

        SharedImportUiState.Closed -> LaunchedEffect(Unit) { onClose() }
    }
}

@Composable
private fun LoadingDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.shared_import_title)) },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        },
        confirmButton = {},
    )
}

@Composable
private fun PasswordDialog(wrongPassword: Boolean, onConfirm: (CharArray) -> Unit, onDismiss: () -> Unit) {
    // Deliberately not saveable: the password must not end up in the saved instance state.
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = secureDialogProperties(),
        title = { Text(stringResource(R.string.shared_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.shared_import_password_message))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.shared_import_password)) },
                    isError = wrongPassword,
                    supportingText = if (wrongPassword) {
                        { Text(stringResource(R.string.shared_import_wrong_password)) }
                    } else {
                        null
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty(), onClick = { onConfirm(password.toCharArray()) }) {
                Text(stringResource(R.string.shared_import_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.shared_import_cancel)) }
        },
    )
}

@Composable
private fun ChooseCardsDialog(
    cards: List<SharedCardUi>,
    fromNewerVersion: Boolean,
    onActionChange: (String, SharedCardAction) -> Unit,
    onLockedChange: (String, Boolean) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = secureDialogProperties(),
        title = { Text(stringResource(R.string.shared_import_title)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (fromNewerVersion) {
                    item {
                        Text(
                            stringResource(R.string.shared_import_newer_version),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                item { Text(stringResource(R.string.shared_import_message)) }
                items(cards, key = { it.preview.key }) { card ->
                    HorizontalDivider()
                    SharedCardRow(card, onActionChange, onLockedChange)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = cards.any { it.action != SharedCardAction.SKIP }, onClick = onImport) {
                Text(stringResource(R.string.shared_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.shared_import_cancel)) }
        },
    )
}

@Composable
private fun SharedCardRow(
    card: SharedCardUi,
    onActionChange: (String, SharedCardAction) -> Unit,
    onLockedChange: (String, Boolean) -> Unit,
) {
    val key = card.preview.key
    val isDuplicate = card.preview.duplicateOf != null
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(card.preview.type.icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                card.preview.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (isDuplicate) {
            Text(
                stringResource(R.string.shared_import_duplicate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            val actions = listOf(
                SharedCardAction.ADD_NEW to R.string.shared_import_add_new,
                SharedCardAction.REPLACE to R.string.shared_import_replace,
                SharedCardAction.SKIP to R.string.shared_import_skip,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                actions.forEachIndexed { index, (action, label) ->
                    SegmentedButton(
                        selected = card.action == action,
                        onClick = { onActionChange(key, action) },
                        shape = SegmentedButtonDefaults.itemShape(index, actions.size),
                        label = { Text(stringResource(label), maxLines = 1) },
                    )
                }
            }
        } else {
            CheckboxRow(
                checked = card.action != SharedCardAction.SKIP,
                label = stringResource(R.string.shared_import_include),
                onCheckedChange = { onActionChange(key, if (it) SharedCardAction.ADD_NEW else SharedCardAction.SKIP) },
            )
        }
        if (card.action != SharedCardAction.SKIP) {
            CheckboxRow(
                checked = card.locked,
                label = stringResource(R.string.shared_import_lock),
                onCheckedChange = { onLockedChange(key, it) },
            )
        }
    }
}

@Composable
private fun CheckboxRow(checked: Boolean, label: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
