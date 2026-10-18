package io.github.danyk20.cardholder.feature.cardlist

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.ui.PasswordField
import io.github.danyk20.cardholder.core.ui.secureDialogProperties

/** Minimum length of an (optional) share password. */
internal const val MIN_SHARE_PASSWORD_LENGTH = 8

private const val SHARE_MIME_TYPE = "application/octet-stream"
private const val STORE_LINK = "https://play.google.com/store/apps/details?id=io.github.danyk20.cardholder"

/** The user must explicitly confirm before bank card data leaves the device. */
@Composable
internal fun BankCardWarningDialog(bankCards: List<Card>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = secureDialogProperties(),
        icon = { Icon(CardholderIcons.Warning, contentDescription = null) },
        title = { Text(stringResource(R.string.cardlist_share_bank_title)) },
        text = {
            Text(
                stringResource(R.string.cardlist_share_bank_message, bankCards.joinToString("\n") { "• ${it.title}" }),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.cardlist_share_bank_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cardlist_share_cancel)) }
        },
    )
}

/** Asks for an optional password; [onConfirm] gets `null` when the user left it empty. */
@Composable
internal fun SharePasswordDialog(onConfirm: (password: CharArray?) -> Unit, onDismiss: () -> Unit) {
    // Deliberately not saveable: the password must not end up in the saved instance state.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val tooShort = password.isNotEmpty() && password.length < MIN_SHARE_PASSWORD_LENGTH
    val mismatch = password.isNotEmpty() && confirmation != password
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = secureDialogProperties(),
        title = { Text(stringResource(R.string.cardlist_share_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.cardlist_share_password_message))
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.cardlist_share_password),
                    error = if (tooShort) {
                        pluralStringResource(
                            R.plurals.cardlist_share_password_too_short,
                            MIN_SHARE_PASSWORD_LENGTH,
                            MIN_SHARE_PASSWORD_LENGTH,
                        )
                    } else {
                        null
                    },
                )
                if (password.isNotEmpty()) {
                    PasswordField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = stringResource(R.string.cardlist_share_password_confirm),
                        error = if (mismatch && confirmation.isNotEmpty()) {
                            stringResource(R.string.cardlist_share_password_mismatch)
                        } else {
                            null
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !tooShort && !mismatch,
                onClick = { onConfirm(password.takeIf { it.isNotEmpty() }?.toCharArray()) },
            ) { Text(stringResource(R.string.cardlist_share_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cardlist_share_cancel)) }
        },
    )
}

/** Opens the system share sheet with the file and a short note on how to open it. */
internal fun Context.launchShareSheet(uri: String, cardCount: Int) {
    val subject = resources.getQuantityString(R.plurals.cardlist_share_subject, cardCount, cardCount)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = SHARE_MIME_TYPE
        putExtra(Intent.EXTRA_STREAM, uri.toUri())
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, getString(R.string.cardlist_share_text, subject, STORE_LINK))
        // Read-only, and only for the app the user picks.
        clipData = ClipData.newRawUri(subject, uri.toUri())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(send, getString(R.string.cardlist_share_chooser)))
}
