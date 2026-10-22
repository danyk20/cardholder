package io.github.danyk20.cardholder.feature.cardeditor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.model.CardValidity
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.model.Region
import io.github.danyk20.cardholder.core.ui.PickerSearchField
import io.github.danyk20.cardholder.core.ui.R as UiR
import io.github.danyk20.cardholder.core.ui.secureDialogProperties
import io.github.danyk20.cardholder.feature.cardeditor.R
import io.github.danyk20.cardholder.feature.cardeditor.countryOptions
import io.github.danyk20.cardholder.feature.cardeditor.regionSelected
import io.github.danyk20.cardholder.feature.cardeditor.resolveValidity
import io.github.danyk20.cardholder.feature.cardeditor.selectedCountries
import io.github.danyk20.cardholder.feature.cardeditor.toggleRegion

/**
 * Full-screen editor for the countries a card is valid in: either all countries, or a selection made
 * with region shortcuts and a searchable list. Done with nothing selected means all countries.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CountryValidityDialog(
    validity: CardValidity,
    countries: List<Country>,
    onDone: (CardValidity) -> Unit,
    onDismiss: () -> Unit,
) {
    var allCountries by rememberSaveable { mutableStateOf(validity == CardValidity.Everywhere) }
    var selected by rememberSaveable(stateSaver = CountrySetSaver) {
        mutableStateOf(validity.selectedCountries())
    }
    var query by rememberSaveable { mutableStateOf("") }
    val options = remember(countries, selected) { countryOptions(countries, selected) }
    val matching = remember(options, query) {
        val needle = query.trim()
        options.filter { country ->
            needle.isEmpty() ||
                country.name.contains(needle, ignoreCase = true) ||
                country.code.value.contains(needle, ignoreCase = true)
        }
    }
    val (chosen, others) = remember(matching, selected) { matching.partition { it.code in selected } }

    Dialog(onDismissRequest = onDismiss, properties = secureDialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.editor_field_validity)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                CardholderIcons.Close,
                                contentDescription = stringResource(UiR.string.action_cancel),
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { onDone(resolveValidity(allCountries, selected)) }) {
                            Text(stringResource(R.string.editor_validity_done))
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SegmentedButton(
                        selected = allCountries,
                        onClick = { allCountries = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text(stringResource(R.string.editor_validity_all)) }
                    SegmentedButton(
                        selected = !allCountries,
                        onClick = { allCountries = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text(stringResource(R.string.editor_validity_selected)) }
                }
                if (!allCountries) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Region.entries.forEach { region ->
                            FilterChip(
                                selected = regionSelected(selected, region),
                                onClick = { selected = toggleRegion(selected, region) },
                                label = { Text(stringResource(region.labelRes)) },
                            )
                        }
                    }
                    PickerSearchField(
                        query = query,
                        hint = stringResource(R.string.editor_search_country),
                        onQueryChange = { query = it },
                    )
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(chosen, key = { it.code.value }) { country ->
                            CountryRow(country, checked = true) { selected = selected - country.code }
                        }
                        if (chosen.isNotEmpty() && others.isNotEmpty()) {
                            item(key = "divider") { HorizontalDivider() }
                        }
                        items(others, key = { it.code.value }) { country ->
                            CountryRow(country, checked = false) { selected = selected + country.code }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryRow(country: Country, checked: Boolean, onToggle: () -> Unit) {
    ListItem(
        headlineContent = { Text(country.name) },
        leadingContent = { Text(country.code.flagEmoji) },
        trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
    )
}

/** The name of a region shortcut. */
internal val Region.labelRes: Int
    get() = when (this) {
        Region.EU -> R.string.editor_region_eu
        Region.SCHENGEN -> R.string.editor_region_schengen
        Region.EUROZONE -> R.string.editor_region_eurozone
        Region.EEA -> R.string.editor_region_eea
    }

/** Keeps a [Set] of countries across configuration changes, as their codes. */
private val CountrySetSaver: Saver<Set<CountryCode>, Any> = listSaver(
    save = { countries -> countries.map(CountryCode::value) },
    restore = { codes -> codes.mapNotNullTo(mutableSetOf()) { CountryCode.of(it) } },
)
