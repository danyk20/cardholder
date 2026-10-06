package io.github.danyk20.cardholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.ui.EraseAllDataDialog
import io.github.danyk20.cardholder.core.ui.R as UiR

/**
 * Shown instead of the app when the encrypted cards can't be opened (the device lost the keys).
 * The data is unrecoverable on this device, so the only way forward is to erase it and start again.
 */
@Composable
internal fun StorageErrorScreen(onEraseAllData: () -> Unit) {
    var confirmErase by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.storage_error_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.storage_error_message), textAlign = TextAlign.Center)
            Button(
                onClick = { confirmErase = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(UiR.string.erase_all_data))
            }
        }
    }
    if (confirmErase) {
        EraseAllDataDialog(onConfirm = onEraseAllData, onDismiss = { confirmErase = false })
    }
}
