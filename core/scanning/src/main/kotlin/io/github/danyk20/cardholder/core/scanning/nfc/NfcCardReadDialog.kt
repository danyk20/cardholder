package io.github.danyk20.cardholder.core.scanning.nfc

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.scanning.R
import java.io.IOException

/** Whether this device can read contactless cards at all. */
fun isNfcAvailable(context: Context): Boolean = NfcAdapter.getDefaultAdapter(context) != null

private enum class ReadState { WAITING, READING, FAILED }

/**
 * Reads a contactless bank card while shown. Card data never leaves memory: it is passed to
 * [onCardRead] and the dialog closes.
 */
@Composable
fun NfcCardReadDialog(onCardRead: (EmvCardData) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return
    val adapter = remember(context) { NfcAdapter.getDefaultAdapter(context) } ?: return
    var enabled by remember { mutableStateOf(adapter.isEnabled) }
    var state by remember { mutableStateOf(ReadState.WAITING) }
    val currentOnCardRead by rememberUpdatedState(onCardRead)

    LifecycleResumeEffect(adapter) {
        enabled = adapter.isEnabled
        onPauseOrDispose {}
    }
    if (enabled) {
        DisposableEffect(adapter, activity) {
            val mainExecutor = ContextCompat.getMainExecutor(activity)
            val callback = NfcAdapter.ReaderCallback { tag ->
                mainExecutor.execute { state = ReadState.READING }
                val data = readCard(tag)
                mainExecutor.execute {
                    if (data != null) currentOnCardRead(data) else state = ReadState.FAILED
                }
            }
            adapter.enableReaderMode(
                activity,
                callback,
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, PRESENCE_CHECK_DELAY_MS) },
            )
            onDispose { adapter.disableReaderMode(activity) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        icon = { Icon(CardholderIcons.Nfc, contentDescription = null) },
        title = { Text(stringResource(R.string.nfc_title)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when {
                    !enabled -> Text(stringResource(R.string.nfc_disabled), textAlign = TextAlign.Center)

                    state == ReadState.READING -> {
                        CircularProgressIndicator(Modifier.size(32.dp))
                        Text(stringResource(R.string.nfc_reading))
                    }

                    else -> {
                        Text(stringResource(R.string.nfc_hold_card), textAlign = TextAlign.Center)
                        if (state == ReadState.FAILED) {
                            Text(
                                stringResource(R.string.nfc_failed),
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.nfc_cvv_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            if (!enabled) {
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) {
                    Text(stringResource(R.string.nfc_turn_on))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.nfc_cancel)) } },
    )
}

/** Runs on the NFC binder thread. */
private fun readCard(tag: Tag): EmvCardData? {
    val isoDep = IsoDep.get(tag) ?: return null
    return try {
        isoDep.use {
            it.connect()
            it.timeout = TRANSCEIVE_TIMEOUT_MS
            EmvCardReader(it::transceive).read()
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        // The tag went out of range while it was being read.
        null
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private const val TRANSCEIVE_TIMEOUT_MS = 5_000
private const val PRESENCE_CHECK_DELAY_MS = 250
