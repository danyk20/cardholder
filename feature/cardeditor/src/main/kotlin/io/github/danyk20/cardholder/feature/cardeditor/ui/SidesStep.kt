package io.github.danyk20.cardholder.feature.cardeditor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.danyk20.cardholder.core.designsystem.component.CardSurface
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.designsystem.theme.CardFaceColors
import io.github.danyk20.cardholder.core.model.CardSide
import io.github.danyk20.cardholder.core.ui.CardImage
import io.github.danyk20.cardholder.core.ui.R as UiR
import io.github.danyk20.cardholder.feature.cardeditor.R
import io.github.danyk20.cardholder.feature.cardeditor.SideImage

/** Actions available to capture a card side. [onScan] is `null` when no scanner is available. */
internal class SideActions(
    val onScan: ((CardSide) -> Unit)?,
    val onChoose: (CardSide) -> Unit,
    val onRemove: (CardSide) -> Unit,
)

@Composable
internal fun SidesStep(
    front: SideImage,
    back: SideImage,
    actions: SideActions,
    onDone: () -> Unit,
    contentPadding: PaddingValues,
) {
    val hasAny = front != SideImage.None || back != SideImage.None
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.editor_sides_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.editor_sides_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SideSlot(CardSide.FRONT, front, actions, Modifier.widthIn(max = 480.dp))
        SideSlot(CardSide.BACK, back, actions, Modifier.widthIn(max = 480.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            if (hasAny) {
                Button(onClick = onDone) { Text(stringResource(R.string.editor_next)) }
            } else {
                TextButton(onClick = onDone) { Text(stringResource(R.string.editor_skip)) }
            }
        }
    }
}

@Composable
internal fun SideSlot(side: CardSide, image: SideImage, actions: SideActions, modifier: Modifier = Modifier) {
    val sideLabel = stringResource(
        if (side ==
            CardSide.FRONT
        ) {
            UiR.string.card_side_front
        } else {
            UiR.string.card_side_back
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Text(sideLabel, style = MaterialTheme.typography.titleSmall)
        CardSurface(colors = PlaceholderColors, modifier = Modifier.fillMaxWidth()) {
            when (image) {
                SideImage.None -> Text(
                    text = stringResource(R.string.editor_side_missing),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )

                is SideImage.Existing -> CardImage(image.ref, contentDescription = sideLabel, Modifier.fillMaxSize())

                is SideImage.New -> AsyncImage(
                    model = image.uri,
                    contentDescription = sideLabel,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (image != SideImage.None) {
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    FilledTonalIconButton(onClick = { actions.onRemove(side) }) {
                        Icon(CardholderIcons.Delete, contentDescription = stringResource(R.string.editor_side_remove))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.onScan?.let { onScan ->
                Button(onClick = { onScan(side) }) {
                    Icon(CardholderIcons.DocumentScanner, contentDescription = null, Modifier.size(18.dp))
                    Text(stringResource(R.string.editor_side_scan), Modifier.padding(start = 8.dp))
                }
            }
            OutlinedButton(onClick = { actions.onChoose(side) }) {
                Icon(CardholderIcons.Photo, contentDescription = null, Modifier.size(18.dp))
                Text(stringResource(R.string.editor_side_choose), Modifier.padding(start = 8.dp))
            }
        }
    }
}

private val PlaceholderColors = CardFaceColors(
    start = androidx.compose.ui.graphics.Color.Transparent,
    end = androidx.compose.ui.graphics.Color.Transparent,
    content = androidx.compose.ui.graphics.Color.Unspecified,
)
