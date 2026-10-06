package io.github.danyk20.cardholder.feature.cardlist

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.danyk20.cardholder.core.designsystem.theme.CardholderTheme
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardDetails
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.testing.data.DemoCards
import io.github.danyk20.cardholder.core.ui.CardSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the Play Store screenshots of the card list. Screens with card data block screen capture
 * on devices (FLAG_SECURE), so the store images are drawn here instead.
 *
 * Record with `./gradlew recordRoborazziDebug`; a normal test run only checks that the screen renders.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xxhdpi")
class StoreScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val state = CardListUiState.Success(
        cards = DemoCards.all.map { (card, details) ->
            CardListItem(CardSummary(card, card.subtitle()), details.code(card))
        },
        query = "",
        visibleTypes = CardType.entries.toSet(),
        hasAnyCards = true,
    )

    @Test
    fun cardListLight() = capture(darkTheme = false, name = "1_cards")

    @Test
    fun cardListDark() = capture(darkTheme = true, name = "5_cards_dark")

    private fun capture(darkTheme: Boolean, name: String) {
        composeRule.setContent {
            CardholderTheme(darkTheme = darkTheme, dynamicColor = false) {
                CardListScreen(
                    uiState = state,
                    sortActions = SortActions({}, {}, { _, _ -> }, {}, {}, {}, {}),
                    onQueryChange = {},
                    onTypeToggled = {},
                    onCardClick = {},
                    onCardLongClick = {},
                    onAddCard = {},
                    onOpenSettings = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$STORE_SCREENSHOTS/$name.png")
    }

    private fun Card.subtitle(): String = when (val info = info) {
        is CardInfo.Bank -> info.issuer?.name.orEmpty()
        is CardInfo.Id -> "Switzerland"
        is CardInfo.Loyalty -> info.shop.name
    }

    private fun CardDetails.code(card: Card): LoyaltyCode? {
        val format = (card.info as? CardInfo.Loyalty)?.format ?: return null
        return LoyaltyCode((this as CardDetails.Loyalty).code, format)
    }
}

/** Play Store phone screenshots, relative to the module directory tests run in. */
private const val STORE_SCREENSHOTS = "../../fastlane/metadata/android/en-US/images/phoneScreenshots"
