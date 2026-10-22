package io.github.danyk20.cardholder.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons.Search

/** Full-screen list with a search field, used to pick a shop or a country. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SearchablePickerDialog(
    title: String,
    searchHint: String,
    items: List<T>,
    key: (T) -> String,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    leading: @Composable ((T) -> Unit)? = null,
    trailing: @Composable ((T) -> Unit)? = null,
    /** Shown above the search field, e.g. an option that is not one of the items. */
    header: @Composable (() -> Unit)? = null,
    customOptionLabel: ((query: String) -> String)? = null,
    onCustomOption: ((query: String) -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss, properties = secureDialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(CardholderIcons.Close, contentDescription = stringResource(R.string.action_cancel))
                        }
                    },
                )
            },
        ) { padding ->
            SearchablePickerContent(
                searchHint = searchHint,
                items = items,
                key = key,
                label = label,
                onSelect = onSelect,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
                leading = leading,
                trailing = trailing,
                header = header,
                customOptionLabel = customOptionLabel,
                onCustomOption = onCustomOption,
            )
        }
    }
}

/**
 * The search field and the filtered list of [SearchablePickerDialog], without the dialog, so it can be shown
 * elsewhere, such as in a bottom sheet. Fills the space given by [modifier]; [header] stays above the search field.
 */
@Composable
fun <T> SearchablePickerContent(
    searchHint: String,
    items: List<T>,
    key: (T) -> String,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable ((T) -> Unit)? = null,
    trailing: @Composable ((T) -> Unit)? = null,
    /** Shown above the search field, e.g. an option that is not one of the items. */
    header: @Composable (() -> Unit)? = null,
    customOptionLabel: ((query: String) -> String)? = null,
    onCustomOption: ((query: String) -> Unit)? = null,
    /** Whether the search field takes focus when shown, which also opens the keyboard. */
    autoFocus: Boolean = true,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val filtered = remember(items, query) {
        val needle = query.trim()
        if (needle.isEmpty()) items else items.filter { label(it).contains(needle, ignoreCase = true) }
    }
    Column(modifier) {
        header?.invoke()
        PickerSearchField(
            query = query,
            hint = searchHint,
            onQueryChange = { query = it },
            modifier = Modifier.focusRequester(focusRequester),
        )
        LazyColumn(Modifier.weight(1f)) {
            if (onCustomOption != null && customOptionLabel != null && query.isNotBlank()) {
                item(key = "custom") {
                    ListItem(
                        headlineContent = { Text(customOptionLabel(query.trim())) },
                        leadingContent = { Icon(CardholderIcons.Edit, contentDescription = null) },
                        modifier = Modifier.clickable { onCustomOption(query.trim()) },
                    )
                    HorizontalDivider()
                }
            }
            items(filtered, key = key) { item ->
                ListItem(
                    headlineContent = { Text(label(item)) },
                    leadingContent = leading?.let { { it(item) } },
                    trailingContent = trailing?.let { { it(item) } },
                    modifier = Modifier.clickable { onSelect(item) },
                )
            }
        }
    }
    if (autoFocus) LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/** The search field at the top of a picker list. */
@Composable
fun PickerSearchField(query: String, hint: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Search, contentDescription = null) },
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
