package io.github.danyk20.cardholder.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.ui.secureDialogProperties

/** Minimum length of a new backup password. */
internal const val MIN_PASSWORD_LENGTH = 10

enum class PasswordPurpose { EXPORT, IMPORT }

/**
 * Asks for the backup password. For [PasswordPurpose.EXPORT] the password must be repeated and long
 * enough; for [PasswordPurpose.IMPORT] the user can choose whether existing cards are replaced.
 */
@Composable
internal fun PasswordDialog(
    purpose: PasswordPurpose,
    onConfirm: (password: CharArray, replaceExisting: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    // Deliberately not saveable: the password must not end up in the saved instance state.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var replaceExisting by remember { mutableStateOf(false) }
    val isExport = purpose == PasswordPurpose.EXPORT
    val tooShort = isExport && password.length < MIN_PASSWORD_LENGTH
    val mismatch = isExport && confirmation != password
    val canConfirm = password.isNotEmpty() && !tooShort && !mismatch

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = secureDialogProperties(),
        title = {
            Text(
                stringResource(
                    if (isExport) R.string.settings_password_title_export else R.string.settings_password_title_import,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isExport) Text(stringResource(R.string.settings_password_message_export))
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.settings_password),
                    error = if (tooShort && password.isNotEmpty()) {
                        pluralStringResource(
                            R.plurals.settings_password_too_short,
                            MIN_PASSWORD_LENGTH,
                            MIN_PASSWORD_LENGTH,
                        )
                    } else {
                        null
                    },
                )
                if (isExport) {
                    PasswordField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = stringResource(R.string.settings_password_confirm),
                        error = if (mismatch && confirmation.isNotEmpty()) {
                            stringResource(R.string.settings_password_mismatch)
                        } else {
                            null
                        },
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.toggleable(
                            value = replaceExisting,
                            role = Role.Checkbox,
                            onValueChange = { replaceExisting = it },
                        ),
                    ) {
                        Checkbox(checked = replaceExisting, onCheckedChange = null)
                        Text(stringResource(R.string.settings_replace_existing))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = { onConfirm(password.toCharArray(), replaceExisting) },
            ) { Text(stringResource(R.string.settings_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String, error: String?) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
