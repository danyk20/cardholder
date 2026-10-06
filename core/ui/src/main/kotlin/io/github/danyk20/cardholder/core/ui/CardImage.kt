package io.github.danyk20.cardholder.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.model.ImageRef

/**
 * Shows an encrypted card photo. The app's image loader knows how to decrypt [ImageRef]s; photos of
 * locked cards only load while the user is authenticated.
 */
@Composable
fun CardImage(
    ref: ImageRef,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    SubcomposeAsyncImage(
        model = ref,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        error = {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    CardholderIcons.Photo,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
            }
        },
    )
}
