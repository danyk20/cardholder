package io.github.danyk20.cardholder.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.designsystem.theme.CardFaceColors

/** Width / height of an ISO/IEC 7810 ID-1 card (85.60 × 53.98 mm), the size of bank cards and most IDs. */
const val ID1_ASPECT_RATIO = 85.60f / 53.98f

val CardShape = RoundedCornerShape(16.dp)

/** A card-shaped container in the ID-1 aspect ratio. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardSurface(
    colors: CardFaceColors,
    modifier: Modifier = Modifier,
    background: Brush = colors.brush,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val clickable = if (onClick != null) {
        Modifier.combinedClickable(onClickLabel = onClickLabel, onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .aspectRatio(ID1_ASPECT_RATIO)
            .shadow(elevation = 4.dp, shape = CardShape)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .background(background)
            .then(clickable),
        content = content,
    )
}
