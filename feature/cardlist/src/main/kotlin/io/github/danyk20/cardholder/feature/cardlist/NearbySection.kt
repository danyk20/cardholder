package io.github.danyk20.cardholder.feature.cardlist

import android.icu.number.NumberFormatter
import android.icu.number.Precision
import android.icu.util.LocaleData
import android.icu.util.MeasureUnit
import android.icu.util.ULocale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.domain.location.NearbyReason
import kotlin.math.roundToInt

internal const val NEARBY_HEADER = "nearby-header"
internal const val ALL_CARDS_HEADER = "all-cards-header"

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(top = 8.dp)
            .semantics { heading() },
    )
}

/** Why a card is suggested: Wi‑Fi of the shop, or how far away it was used. */
@Composable
internal fun NearbyReasonLabel(reason: NearbyReason, modifier: Modifier = Modifier) {
    val text = when (reason) {
        is NearbyReason.WifiHere, is NearbyReason.WifiChain, is NearbyReason.WifiName ->
            stringResource(R.string.cardlist_nearby_wifi)

        is NearbyReason.Distance -> stringResource(R.string.cardlist_nearby_distance, rememberDistance(reason.meters))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Icon(
            CardholderIcons.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A short distance in the user's units: "350 m", or "0.2 mi" where roads are measured in miles. */
@Composable
private fun rememberDistance(meters: Double): String {
    val locale = Locale.current.platformLocale
    return remember(meters, locale) {
        val uLocale = ULocale.forLocale(locale)
        val usesMiles = LocaleData.getMeasurementSystem(uLocale) != LocaleData.MeasurementSystem.SI
        val formatter = NumberFormatter.withLocale(uLocale).unitWidth(NumberFormatter.UnitWidth.SHORT)
        if (usesMiles) {
            formatter.unit(MeasureUnit.MILE).precision(Precision.maxFraction(1))
                .format((meters / METERS_PER_MILE).coerceAtLeast(MIN_MILES))
        } else {
            // Rounded to tens of metres: the places are only that precise anyway.
            val rounded = ((meters / ROUND_TO_METERS).roundToInt() * ROUND_TO_METERS).coerceAtLeast(ROUND_TO_METERS)
            formatter.unit(MeasureUnit.METER).precision(Precision.integer()).format(rounded)
        }.toString()
    }
}

private const val ROUND_TO_METERS = 10.0
private const val METERS_PER_MILE = 1_609.344
private const val MIN_MILES = 0.1
