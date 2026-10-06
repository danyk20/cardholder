package io.github.danyk20.cardholder.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.danyk20.cardholder.core.model.ImageRef

/**
 * A logo on a white plate, readable on any card colour. [model] is anything Coil can load: an
 * [ImageRef] of a stored logo, a URI string or the bytes of a downloaded logo.
 */
@Composable
fun LogoBadge(model: Any, contentDescription: String?, modifier: Modifier = Modifier, height: Dp = 40.dp) {
    Box(
        modifier
            .height(height)
            .widthIn(max = height * MAX_ASPECT_RATIO)
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        AsyncImage(model = model, contentDescription = contentDescription, contentScale = ContentScale.Fit)
    }
}

private const val MAX_ASPECT_RATIO = 4
