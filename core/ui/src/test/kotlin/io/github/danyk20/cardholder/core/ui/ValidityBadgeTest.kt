package io.github.danyk20.cardholder.core.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.designsystem.theme.CardFaceColors
import io.github.danyk20.cardholder.core.designsystem.theme.CardholderTheme
import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.model.CountryCode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ValidityBadgeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val austria = country("AT", "Austria")
    private val germany = country("DE", "Germany")
    private val switzerland = country("CH", "Switzerland")

    @Test
    fun `one country shows its flag and name and is spoken as valid only there`() {
        composeRule.setContent { CardholderTheme { ValidityBadge(listOf(switzerland)) } }

        composeRule.onNodeWithText(switzerland.flagAndName()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Valid only in Switzerland").assertIsDisplayed()
    }

    @Test
    fun `several countries show a globe and are spoken with their names`() {
        composeRule.setContent { CardholderTheme { ValidityBadge(listOf(austria, germany, switzerland)) } }

        composeRule.onNodeWithText("🌐").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Valid in 3 countries: Austria, Germany, Switzerland")
            .assertIsDisplayed()
    }

    @Test
    fun `a card valid everywhere has no badge`() {
        composeRule.setContent { CardholderTheme { ValidityBadge(emptyList()) } }

        composeRule.onAllNodes(hasContentDescription("Valid", substring = true)).assertCountEquals(0)
    }

    @Test
    fun `the card face describes where it is valid`() {
        composeRule.setContent {
            CardholderTheme {
                CardFace(
                    title = "Coop Supercard",
                    subtitle = "Loyalty card",
                    type = CardType.LOYALTY,
                    colors = CardFaceColors.fromArgb(0xFFFF6600),
                    isLocked = false,
                    validCountries = listOf(switzerland),
                )
            }
        }

        // The face is one node for screen readers, so its own description names the validity too.
        composeRule.onNode(
            hasContentDescription("Coop Supercard", substring = true) and
                hasContentDescription("Valid only in Switzerland", substring = true),
        ).assertIsDisplayed()
    }

    private fun country(code: String, name: String) = Country(requireNotNull(CountryCode.of(code)), name)
}
