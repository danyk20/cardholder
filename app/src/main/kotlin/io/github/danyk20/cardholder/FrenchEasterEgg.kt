package io.github.danyk20.cardholder

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties

/**
 * A joke for French speakers, shown once when the app first runs in French. It can only be closed by
 * promising to learn another language; after that the app works normally, in French.
 */
@Composable
internal fun FrenchEasterEgg(onPromise: () -> Unit) {
    if (LocalConfiguration.current.locales[0].language != FRENCH) return
    AlertDialog(
        onDismissRequest = {},
        text = { Text(stringResource(R.string.french_easter_egg_message)) },
        confirmButton = {
            TextButton(onClick = onPromise) { Text(stringResource(R.string.french_easter_egg_promise)) }
        },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    )
}

private const val FRENCH = "fr"
