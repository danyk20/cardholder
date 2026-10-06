package io.github.danyk20.cardholder.feature.cardlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.barcode.BarcodeEncoder
import io.github.danyk20.cardholder.core.barcode.BarcodeImage
import io.github.danyk20.cardholder.core.designsystem.component.CardSurface
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.ui.CardSummary
import io.github.danyk20.cardholder.core.ui.LogoBadge
import io.github.danyk20.cardholder.core.ui.R as UiR
import io.github.danyk20.cardholder.core.ui.barcodeFormatLabel
import io.github.danyk20.cardholder.core.ui.icon

/**
 * Face of a loyalty card in the list: shop name on the card's colour and the scannable code on a
 * white panel, so the card can be shown at the till straight from the list. Locked cards show a
 * lock instead of the code.
 */
@Composable
internal fun LoyaltyCardFace(
    summary: CardSummary,
    code: LoyaltyCode?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val card = summary.card
    val colors = summary.faceColors
    val description = listOfNotNull(
        card.title,
        stringResource(UiR.string.card_type_loyalty),
        summary.subtitle.takeIf { it != card.title },
        stringResource(UiR.string.card_locked).takeIf { card.isLocked },
    ).joinToString()
    CardSurface(
        colors = colors,
        onClick = onClick,
        onLongClick = onLongClick,
        onClickLabel = stringResource(R.string.cardlist_show_code),
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val logo = card.logo
                if (logo != null) {
                    LogoBadge(model = logo, contentDescription = null, height = 36.dp)
                } else {
                    Icon(
                        card.type.icon,
                        contentDescription = null,
                        tint = colors.content,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (code == null) LockedCode() else CodePanel(code)
            }
        }
    }
}

@Composable
private fun CodePanel(code: LoyaltyCode) {
    val encodable = remember(code) { BarcodeEncoder.canEncode(code.value, code.format) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
        if (encodable) {
            BarcodeImage(
                content = code.value,
                format = code.format,
                contentDescription = barcodeFormatLabel(code.format),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
        // Square codes carry no human-readable line; linear ones show it for manual entry.
        if (!code.format.isSquare || !encodable) {
            Text(
                text = code.value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun LockedCode() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(CardholderIcons.Lock, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(32.dp))
        Text(
            text = stringResource(R.string.cardlist_tap_to_unlock),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.DarkGray,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
