package io.github.danyk20.cardholder.update

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.danyk20.cardholder.BuildConfig
import io.github.danyk20.cardholder.R

/** "Check for updates" and "Check automatically" in Settings > About. */
@Composable
fun UpdateSection(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val status = state.status
    ListItem(
        headlineContent = { Text(stringResource(R.string.update_check)) },
        supportingContent = { Text(statusText(state)) },
        trailingContent = when (status) {
            UpdateStatus.Checking -> ({ CircularProgressIndicator(Modifier.size(24.dp)) })

            is UpdateStatus.Available -> (
                {
                    TextButton(onClick = {
                        activity?.let(viewModel::install)
                    }) { Text(stringResource(R.string.update_now)) }
                }
                )

            else -> null
        },
        modifier = Modifier.selectable(selected = false, role = Role.Button, onClick = viewModel::check),
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.update_auto)) },
        supportingContent = { Text(stringResource(R.string.update_auto_description)) },
        trailingContent = { Switch(checked = state.autoCheck, onCheckedChange = null) },
        modifier = Modifier.toggleable(
            value = state.autoCheck,
            role = Role.Switch,
            onValueChange = viewModel::onAutoCheckChange,
        ),
    )
    // In Settings the result is shown in the list item; only the steps of installing need a dialog.
    if (status !is UpdateStatus.Available) UpdateDialogs(state, activity, viewModel)
}

/** Checks once a week when the app opens, if the user switched that on, and offers an update it found. */
@Composable
fun UpdatePrompt(viewModel: UpdateViewModel = hiltViewModel()) {
    LaunchedEffect(viewModel) { viewModel.checkIfDue() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UpdateDialogs(state, LocalActivity.current, viewModel)
}

@Composable
private fun UpdateDialogs(state: UpdateUiState, activity: Activity?, viewModel: UpdateViewModel) {
    when (val status = state.status) {
        is UpdateStatus.Available -> AvailableDialog(
            update = status.update,
            onUpdate = { activity?.let(viewModel::install) },
            onDismiss = viewModel::onDismiss,
        )

        is UpdateStatus.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_available_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.update_downloading, (status.progress * PERCENT).toInt()))
                    LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {},
        )

        is UpdateStatus.NeedsPermission -> {
            val context = LocalContext.current
            AlertDialog(
                onDismissRequest = viewModel::onDismiss,
                title = { Text(stringResource(R.string.update_permission_title)) },
                text = { Text(stringResource(R.string.update_permission_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                                    .setData("package:${context.packageName}".toUri()),
                            )
                        },
                    ) { Text(stringResource(R.string.update_open_settings)) }
                },
                dismissButton = {
                    TextButton(onClick = { activity?.let(viewModel::install) }) {
                        Text(stringResource(R.string.update_now))
                    }
                },
            )
        }

        UpdateStatus.InstallFailed -> AlertDialog(
            onDismissRequest = viewModel::onDismiss,
            title = { Text(stringResource(R.string.update_available_title)) },
            text = { Text(stringResource(R.string.update_install_failed)) },
            confirmButton = {
                TextButton(onClick = viewModel::onDismiss) { Text(stringResource(android.R.string.ok)) }
            },
        )

        UpdateStatus.Idle, UpdateStatus.Checking, UpdateStatus.UpToDate, UpdateStatus.CheckFailed,
        UpdateStatus.Installing,
        -> Unit
    }
}

@Composable
private fun AvailableDialog(update: UpdateCheck.Available, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            ) {
                Text(availableText(update))
                update.notes?.let { notes ->
                    Text(
                        plainNotes(notes),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onUpdate) { Text(stringResource(R.string.update_now)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) } },
    )
}

@Composable
private fun statusText(state: UpdateUiState): String = when (val status = state.status) {
    UpdateStatus.Checking -> stringResource(R.string.update_checking)

    UpdateStatus.UpToDate -> stringResource(R.string.update_up_to_date, BuildConfig.VERSION_NAME)

    UpdateStatus.CheckFailed -> stringResource(R.string.update_check_failed)

    is UpdateStatus.Available -> availableText(status.update)

    is UpdateStatus.Downloading -> stringResource(R.string.update_downloading, (status.progress * PERCENT).toInt())

    UpdateStatus.Installing -> stringResource(R.string.update_installing)

    is UpdateStatus.NeedsPermission, UpdateStatus.InstallFailed, UpdateStatus.Idle -> listOfNotNull(
        stringResource(
            when (state.channel) {
                UpdateChannel.GOOGLE_PLAY -> R.string.update_source_play
                UpdateChannel.GITHUB -> R.string.update_source_github
            },
        ),
        state.lastCheck?.let {
            stringResource(
                R.string.update_last_checked,
                DateUtils.getRelativeTimeSpanString(it.toEpochMilli()).toString(),
            )
        },
    ).joinToString("\n")
}

@Composable
private fun availableText(update: UpdateCheck.Available): String =
    update.version?.let { stringResource(R.string.update_available_version, it) }
        ?: stringResource(R.string.update_available)

/** Release notes are Markdown; headings and list markers read better as plain text in a dialog. */
internal fun plainNotes(markdown: String): String = markdown.lines()
    .map { line ->
        line.trimEnd()
            .replace(Regex("^#+\\s*"), "")
            .replace(Regex("^(\\s*)[-*]\\s+"), "$1• ")
            .replace("**", "")
            .replace("`", "")
    }
    .joinToString("\n")
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()

private const val PERCENT = 100
