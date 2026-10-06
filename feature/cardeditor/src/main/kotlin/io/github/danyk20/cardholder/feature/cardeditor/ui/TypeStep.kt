package io.github.danyk20.cardholder.feature.cardeditor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.ui.description
import io.github.danyk20.cardholder.core.ui.icon
import io.github.danyk20.cardholder.core.ui.label
import io.github.danyk20.cardholder.feature.cardeditor.R

@Composable
internal fun TypeStep(onTypeSelected: (CardType) -> Unit, contentPadding: PaddingValues) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.editor_choose_type), style = MaterialTheme.typography.titleLarge)
        CardType.entries.forEach { type ->
            ElevatedCard(onClick = { onTypeSelected(type) }, modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = {
                        Text(stringResource(type.label), style = MaterialTheme.typography.titleMedium)
                    },
                    supportingContent = { Text(stringResource(type.description)) },
                    leadingContent = {
                        Icon(
                            type.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}
