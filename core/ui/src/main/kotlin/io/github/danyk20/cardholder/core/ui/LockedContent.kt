package io.github.danyk20.cardholder.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons

/** Placeholder for protected content; [onUnlock] is `null` when the data can't be unlocked anymore. */
@Composable
fun LockedContent(message: String, onUnlock: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Icon(
            CardholderIcons.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        onUnlock?.let {
            Button(onClick = it) { Text(stringResource(R.string.action_unlock)) }
        }
    }
}
