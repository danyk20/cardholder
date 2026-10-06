package io.github.danyk20.cardholder.feature.cardeditor.ui

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
import io.github.danyk20.cardholder.core.ui.R as UiR
import io.github.danyk20.cardholder.core.ui.secureDialogProperties

/** Full-screen list with a search field, used to pick a shop or a country. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SearchablePickerDialog(
    title: String,
    searchHint: String,
    items: List<T>,
    key: (T) -> String,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    leading: @Composable ((T) -> Unit)? = null,
    customOptionLabel: ((query: String) -> String)? = null,
    onCustomOption: ((query: String) -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val filtered = remember(items, query) {
        val needle = query.trim()
        if (needle.isEmpty()) items else items.filter { label(it).contains(needle, ignoreCase = true) }
    }
    Dialog(onDismissRequest = onDismiss, properties = secureDialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(CardholderIcons.Close, contentDescription = stringResource(UiR.string.action_cancel))
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(searchHint) },
                    leadingIcon = { Icon(Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .focusRequester(focusRequester),
                )
                LazyColumn(Modifier.fillMaxSize()) {
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
                            modifier = Modifier.clickable { onSelect(item) },
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
