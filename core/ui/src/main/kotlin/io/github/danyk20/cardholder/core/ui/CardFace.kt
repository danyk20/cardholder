package io.github.danyk20.cardholder.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.designsystem.component.CardSurface
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.designsystem.theme.CardFaceColors
import io.github.danyk20.cardholder.core.designsystem.theme.CardholderTheme
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.model.ImageRef

/**
 * The face of a card as shown in lists: the photo of its front side when available and the card is
 * not locked, otherwise a generated face in the card's colour.
 */
@Composable
fun CardFace(
    title: String,
    subtitle: String,
    type: CardType,
    colors: CardFaceColors,
    isLocked: Boolean,
    modifier: Modifier = Modifier,
    frontImage: ImageRef? = null,
    logo: ImageRef? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val typeLabel = stringResource(type.label)
    val lockedLabel = stringResource(R.string.card_locked)
    val showPhoto = frontImage != null && !isLocked
    CardSurface(
        colors = colors,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = listOfNotNull(title, typeLabel, subtitle, lockedLabel.takeIf { isLocked })
                .joinToString()
        },
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        if (showPhoto) {
            CardImage(ref = frontImage, contentDescription = null, modifier = Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xCC000000))),
            )
        }
        val contentColor = if (showPhoto) Color.White else colors.content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                when {
                    logo != null -> LogoBadge(model = logo, contentDescription = null)

                    !showPhoto -> Icon(
                        type.icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Box(Modifier.weight(1f))
                if (isLocked) LockBadge()
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty() && subtitle != title) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LockBadge() {
    Surface(shape = MaterialTheme.shapes.small, color = Color.Black.copy(alpha = 0.35f), contentColor = Color.White) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(CardholderIcons.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(
                text = stringResource(R.string.card_locked),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Preview
@Composable
private fun CardFacePreview() {
    CardholderTheme {
        CardFace(
            title = "Migros Cumulus",
            subtitle = "Loyalty card",
            type = CardType.LOYALTY,
            colors = CardFaceColors.fromArgb(0xFFFF6600),
            isLocked = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}
