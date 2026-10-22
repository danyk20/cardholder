package io.github.danyk20.cardholder.feature.cardlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.domain.country.CountrySource
import io.github.danyk20.cardholder.core.domain.country.CountryState
import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.ui.SearchablePickerDialog
import io.github.danyk20.cardholder.core.ui.flagAndName

/** What the country row and its picker do. */
internal class CountryActions(
    val onFilterChange: (Boolean) -> Unit,
    val onPick: (CountryCode) -> Unit,
    val onAutomatic: () -> Unit,
    val onShowAll: () -> Unit,
)

/**
 * The country the cards are filtered by: a chip that turns the filter on or off, and a button to choose
 * another country. Nothing is shown yet when no country is known, except the chip saying so.
 */
@Composable
internal fun CountryChipRow(chip: CountryChip, countries: List<Country>, actions: CountryActions) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val effective = chip.state.effective
    val name = effective?.let { countryOf(it.code, countries).flagAndName() }
    val filterDescription = effective?.takeIf { chip.filterEnabled }?.let { current ->
        stringResource(R.string.cardlist_country_filter_description, countryOf(current.code, countries).name)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilterChip(
            selected = chip.filterEnabled,
            onClick = { actions.onFilterChange(!chip.filterEnabled) },
            label = { Text(name ?: stringResource(R.string.cardlist_country_unknown)) },
            modifier = Modifier.semantics {
                filterDescription?.let { stateDescription = it }
            },
        )
        IconButton(onClick = { showPicker = true }) {
            Icon(CardholderIcons.Edit, contentDescription = stringResource(R.string.cardlist_country_change))
        }
    }
    if (showPicker) {
        CountryPicker(
            state = chip.state,
            countries = countries,
            onAutomatic = {
                actions.onAutomatic()
                showPicker = false
            },
            onPick = {
                actions.onPick(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

/** Lets the user pick the country the cards are filtered by: the detected one, or any other. */
@Composable
private fun CountryPicker(
    state: CountryState,
    countries: List<Country>,
    onAutomatic: () -> Unit,
    onPick: (CountryCode) -> Unit,
    onDismiss: () -> Unit,
) {
    val chosen = state.effective?.code.takeIf { state.isManual }
    SearchablePickerDialog(
        title = stringResource(R.string.cardlist_country_title),
        searchHint = stringResource(R.string.cardlist_country_search),
        items = countries,
        key = { it.code.value },
        label = { it.name },
        onSelect = { onPick(it.code) },
        onDismiss = onDismiss,
        leading = { Text(it.code.flagEmoji) },
        trailing = { if (it.code == chosen) Icon(CardholderIcons.Check, contentDescription = null) },
        header = {
            AutomaticOption(
                text = automaticText(state, countries),
                chosen = !state.isManual,
                onClick = onAutomatic,
            )
        },
    )
}

@Composable
private fun AutomaticOption(text: String, chosen: Boolean, onClick: () -> Unit) {
    Column {
        ListItem(
            headlineContent = { Text(text) },
            trailingContent = { if (chosen) Icon(CardholderIcons.Check, contentDescription = null) },
            modifier = Modifier.selectable(selected = chosen, onClick = onClick),
        )
        HorizontalDivider()
    }
}

/** "Automatic: Switzerland (mobile network)", or "Automatic: unknown" when nothing was detected. */
@Composable
private fun automaticText(state: CountryState, countries: List<Country>): String {
    val detected = state.detected ?: return stringResource(R.string.cardlist_country_automatic_unknown)
    return stringResource(
        R.string.cardlist_country_automatic,
        countryOf(detected.code, countries).flagAndName(),
        stringResource(detected.source.labelRes()),
    )
}

/** How an automatic country was found. A manual choice is never stored as a detection. */
private fun CountrySource.labelRes(): Int = when (this) {
    CountrySource.NETWORK -> R.string.cardlist_source_network
    CountrySource.LOCATION -> R.string.cardlist_source_location
    CountrySource.LAST_KNOWN, CountrySource.MANUAL -> R.string.cardlist_source_last_known
}

/** The country with its localized name, or its code when the catalogue lacks it (such as `XK`). */
private fun countryOf(code: CountryCode, countries: List<Country>): Country =
    countries.firstOrNull { it.code == code } ?: Country(code, code.value)
