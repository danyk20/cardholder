package io.github.danyk20.cardholder.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.domain.model.Country

/** The flag and name of a country, as on the badges and in the lists of countries. */
fun Country.flagAndName(): String = "${code.flagEmoji}  $name"

/**
 * Badge on a card's face naming where the card is valid: the flag and name of one country, or a globe
 * for several. Nothing is shown for a card valid everywhere, when [countries] is empty.
 *
 * ID cards keep their large issuing-country flag. The badge is separate because the issuing country
 * and the countries where the card is accepted can differ.
 */
@Composable
fun ValidityBadge(countries: List<Country>, modifier: Modifier = Modifier) {
    val description = validityDescription(countries) ?: return
    val label = countries.singleOrNull()?.flagAndName() ?: GLOBE
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        shape = MaterialTheme.shapes.small,
        color = Color.Black.copy(alpha = 0.35f),
        contentColor = Color.White,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** What a [ValidityBadge] says for [countries], spoken by screen readers; `null` when valid everywhere. */
@Composable
fun validityDescription(countries: List<Country>): String? = when {
    countries.isEmpty() -> null

    countries.size == 1 -> stringResource(R.string.card_valid_only_in, countries.single().name)

    else -> pluralStringResource(
        R.plurals.card_valid_in_countries,
        countries.size,
        countries.size,
        countries.joinToString(", ") { it.name },
    )
}

private const val GLOBE = "🌐"
