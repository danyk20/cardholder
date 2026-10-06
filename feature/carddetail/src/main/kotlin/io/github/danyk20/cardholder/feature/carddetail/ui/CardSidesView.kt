package io.github.danyk20.cardholder.feature.carddetail.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import io.github.danyk20.cardholder.core.designsystem.component.CardSurface
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.model.ImageRef
import io.github.danyk20.cardholder.core.ui.CardFace
import io.github.danyk20.cardholder.core.ui.CardImage
import io.github.danyk20.cardholder.core.ui.CardSummary
import io.github.danyk20.cardholder.feature.carddetail.R

private const val FLIP_DEGREES = 180f
private const val HALF_FLIP_DEGREES = 90f
private const val FLIP_MILLIS = 400
private const val CAMERA_DISTANCE = 12f

/**
 * Shows the card's photos with a flip animation between front and back, or the generated card face
 * when there are no photos or they are protected and not unlocked.
 */
@Composable
internal fun CardSidesView(summary: CardSummary, photosVisible: Boolean, modifier: Modifier = Modifier) {
    val sides = summary.card.sides
    if (!photosVisible || sides.refs.isEmpty()) {
        CardFace(summary.copy(card = summary.card.copy(sides = sides.copy(front = null))), modifier)
        return
    }
    var showBack by rememberSaveable { mutableStateOf(false) }
    var zoomed by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (showBack) FLIP_DEGREES else 0f, tween(FLIP_MILLIS), label = "flip")
    val visible = if (rotation <= HALF_FLIP_DEGREES) sides.front else sides.back
    Box(modifier) {
        CardSurface(
            colors = summary.faceColors,
            onClick = { zoomed = true },
            modifier = Modifier.graphicsLayer {
                rotationY = rotation
                cameraDistance = CAMERA_DISTANCE * density
            },
        ) {
            val content = Modifier
                .fillMaxSize()
                .graphicsLayer { if (rotation > HALF_FLIP_DEGREES) rotationY = FLIP_DEGREES }
            if (visible != null) {
                CardImage(visible, contentDescription = summary.card.title, modifier = content)
            } else {
                CardFace(summary.copy(card = summary.card.copy(sides = sides.copy(front = null))), content)
            }
        }
        if (sides.front != null && sides.back != null) {
            FilledTonalIconButton(
                onClick = { showBack = !showBack },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            ) {
                Icon(CardholderIcons.Flip, contentDescription = stringResource(R.string.detail_flip))
            }
        }
    }
    if (zoomed && visible != null) PhotoDialog(visible, summary.card.title) { zoomed = false }
}

@Composable
private fun PhotoDialog(ref: ImageRef, description: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
        ) {
            CardImage(
                ref,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(CardholderIcons.Close, stringResource(R.string.detail_close_photo), tint = Color.White)
            }
        }
    }
}
