package io.github.danyk20.cardholder.feature.carddetail

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.danyk20.cardholder.core.designsystem.theme.CardholderTheme
import io.github.danyk20.cardholder.core.domain.model.Country
import io.github.danyk20.cardholder.core.model.CardValidity
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.testing.data.DemoCards
import io.github.danyk20.cardholder.core.ui.CardSummary
import io.github.danyk20.cardholder.feature.carddetail.ui.FieldActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the Play Store screenshots of card details (FLAG_SECURE blocks capturing it on a
 * device). Record with `./gradlew recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xxhdpi")
class StoreScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bankCardDetails() = capture(
        CardDetailUiState(
            // Valid in three countries, so the detail screen lists them.
            summary = CardSummary(
                card = DemoCards.bank.copy(validity = CardValidity.Countries(setOf(SWITZERLAND, GERMANY, AUSTRIA))),
                subtitle = "City Savings Bank",
                validCountries = listOf(
                    Country(SWITZERLAND, "Switzerland"),
                    Country(GERMANY, "Germany"),
                    Country(AUSTRIA, "Austria"),
                ),
            ),
            details = DetailsState.Loaded(DemoCards.bankDetails),
            canProtect = true,
        ),
        name = "3_bank_card",
    )

    @Test
    fun idCardDetails() = capture(
        CardDetailUiState(
            summary = CardSummary(
                card = DemoCards.identity.copy(validity = CardValidity.Countries(setOf(SWITZERLAND))),
                subtitle = "Switzerland",
                validCountries = listOf(Country(SWITZERLAND, "Switzerland")),
            ),
            details = DetailsState.Loaded(DemoCards.identityDetails),
            canProtect = true,
        ),
        name = "4_id_card",
    )

    private fun capture(state: CardDetailUiState, name: String) {
        composeRule.setContent {
            CardholderTheme(darkTheme = false, dynamicColor = false) {
                CardDetailScreen(
                    state = state,
                    onBack = {},
                    onEdit = {},
                    onShowBarcode = {},
                    onDelete = {},
                    onLockedChange = {},
                    onFavouriteChange = {},
                    onUnlock = {},
                    fieldActions = FieldActions(onCopy = { _, _ -> }, onRevealCvv = {}, onHideCvv = {}),
                    onCopiedMessageShown = {},
                    onErrorShown = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$STORE_SCREENSHOTS/$name.png")
    }
}

private val SWITZERLAND = requireNotNull(CountryCode.of("CH"))
private val GERMANY = requireNotNull(CountryCode.of("DE"))
private val AUSTRIA = requireNotNull(CountryCode.of("AT"))

/** Play Store phone screenshots, relative to the module directory tests run in. */
private const val STORE_SCREENSHOTS = "../../fastlane/metadata/android/en-US/images/phoneScreenshots"
