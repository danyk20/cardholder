package io.github.danyk20.cardholder.feature.cardlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.domain.country.CountrySource
import io.github.danyk20.cardholder.core.domain.country.CountryState
import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.ui.SearchablePickerContent
import io.github.danyk20.cardholder.core.ui.flagAndName
import io.github.danyk20.cardholder.core.ui.secureSheetProperties
import kotlinx.coroutines.launch

/** What the country filter does: turning it on or off, and choosing the country it filters by. */
internal class CountryActions(
    val onFilterChange: (Boolean) -> Unit,
    val onPick: (CountryCode) -> Unit,
    val onAutomatic: () -> Unit,
    val onShowAll: () -> Unit,
)

/** Marks the dot shown while the cards are filtered by a country, for tests. */
internal const val COUNTRY_FILTER_BADGE_TAG = "country_filter_badge"

/** How much of the screen the country sheet takes; its list scrolls within that. */
private const val SHEET_HEIGHT_FRACTION = 0.9f

/**
 * The country filter in the title bar: an icon that opens [CountryFilterSheet], with a dot while the cards are
 * filtered by a known country.
 */
@Composable
internal fun CountryFilterButton(chip: CountryChip, countries: List<Country>, actions: CountryActions) {
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val filtering = chip.state.effective?.takeIf { chip.filterEnabled }
    val description = filtering?.let { current ->
        stringResource(R.string.cardlist_country_filter_description, countryOf(current.code, countries).name)
    } ?: stringResource(R.string.cardlist_country_filter_all)
    IconButton(
        onClick = { showSheet = true },
        modifier = Modifier.semantics { stateDescription = description },
    ) {
        BadgedBox(
            badge = {
                if (filtering != null) Badge(Modifier.testTag(COUNTRY_FILTER_BADGE_TAG))
            },
        ) {
            Icon(CardholderIcons.Filter, contentDescription = stringResource(R.string.cardlist_country_filter_title))
        }
    }
    if (showSheet) {
        CountryFilterSheet(chip, countries, actions, onDismiss = { showSheet = false })
    }
}

/**
 * The country filter's bottom sheet: a switch for filtering by the country in use, then the choice of that
 * country, from the detected one or any other. Choosing a country, or the detected one, turns the filter on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryFilterSheet(
    chip: CountryChip,
    countries: List<Country>,
    actions: CountryActions,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Slides the sheet away once the choice is made, instead of removing it mid-frame.
    fun closeAfter(choice: () -> Unit) {
        choice()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    val chosen = chip.state.effective?.code.takeIf { chip.state.isManual }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        properties = secureSheetProperties(),
    ) {
        SearchablePickerContent(
            searchHint = stringResource(R.string.cardlist_country_search),
            items = countries,
            key = { it.code.value },
            label = { it.name },
            onSelect = { country -> closeAfter { actions.onPick(country.code) } },
            modifier = Modifier
                .fillMaxHeight(SHEET_HEIGHT_FRACTION)
                .imePadding(),
            leading = { Text(it.code.flagEmoji) },
            trailing = { if (it.code == chosen) Icon(CardholderIcons.Check, contentDescription = null) },
            header = {
                Column {
                    Text(
                        text = stringResource(R.string.cardlist_country_filter_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    CountryFilterSwitch(chip, countries, actions.onFilterChange)
                    HorizontalDivider()
                    AutomaticOption(
                        text = automaticText(chip.state, countries),
                        chosen = !chip.state.isManual,
                        onClick = { closeAfter(actions.onAutomatic) },
                    )
                }
            },
            autoFocus = false,
        )
    }
}

/** Turns the filter on or off for the country in use; without a known country, it says all cards are shown. */
@Composable
private fun CountryFilterSwitch(chip: CountryChip, countries: List<Country>, onChange: (Boolean) -> Unit) {
    val effective = chip.state.effective
    val headline = effective?.let {
        stringResource(R.string.cardlist_country_filter_switch, countryOf(it.code, countries).flagAndName())
    } ?: stringResource(R.string.cardlist_country_filter_switch_unknown)
    val supporting: (@Composable () -> Unit)? = if (effective == null) {
        { Text(stringResource(R.string.cardlist_country_filter_unknown)) }
    } else {
        null
    }
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = supporting,
        trailingContent = { Switch(checked = chip.filterEnabled, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = chip.filterEnabled, role = Role.Switch, onValueChange = onChange),
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
