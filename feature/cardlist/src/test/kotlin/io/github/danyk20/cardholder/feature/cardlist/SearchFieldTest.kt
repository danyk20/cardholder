package io.github.danyk20.cardholder.feature.cardlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.designsystem.theme.CardholderTheme
import io.github.danyk20.cardholder.core.model.CardType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchFieldTest {
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * The filtered list arrives asynchronously, after the cards were filtered and decrypted. Typing must
     * not depend on it: showing the list's (older) query in the field used to reorder fast typing, so
     * "credit" became "reditc".
     */
    @Test
    fun `typing is shown as typed while the list is still catching up`() {
        // The list never catches up: its query stays empty for the whole test.
        val laggingList = CardListUiState.Success(
            cards = emptyList(),
            query = "",
            visibleTypes = CardType.entries.toSet(),
            hasAnyCards = true,
        )
        composeRule.setContent {
            var typed by remember { mutableStateOf("") }
            CardholderTheme {
                CardListScreen(
                    uiState = laggingList,
                    query = typed,
                    sortActions = SortActions({}, {}, { _, _ -> }, {}, {}, {}, {}),
                    onQueryChange = { typed = it },
                    onTypeToggled = {},
                    onCardClick = {},
                    onCardLongClick = {},
                    onAddCard = {},
                    onOpenSettings = {},
                )
            }
        }

        val field = composeRule.onNode(hasSetTextAction())
        "credit".forEach { letter -> field.performTextInput(letter.toString()) }

        field.assertTextEquals("credit", includeEditableText = true)
    }
}
