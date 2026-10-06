package io.github.danyk20.cardholder.feature.carddetail

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.danyk20.cardholder.core.designsystem.theme.CardholderTheme
import io.github.danyk20.cardholder.core.testing.data.DemoCards
import io.github.danyk20.cardholder.core.ui.CardSummary
import io.github.danyk20.cardholder.feature.carddetail.ui.FieldActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the Play Store screenshot of a bank card's details (FLAG_SECURE blocks capturing it on a
 * device). Record with `./gradlew recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xxhdpi")
class StoreScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bankCardDetails() {
        val state = CardDetailUiState(
            summary = CardSummary(DemoCards.bank, "City Savings Bank"),
            details = DetailsState.Loaded(DemoCards.bankDetails),
            canProtect = true,
        )
        composeRule.setContent {
            CardholderTheme(darkTheme = false, dynamicColor = false) {
                CardDetailScreen(
                    state = state,
                    onBack = {},
                    onEdit = {},
                    onShowBarcode = {},
                    onDelete = {},
                    onLockedChange = {},
                    onUnlock = {},
                    fieldActions = FieldActions(onCopy = { _, _ -> }, onRevealCvv = {}, onHideCvv = {}),
                    onCopiedMessageShown = {},
                    onKeyInvalidatedErrorShown = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$STORE_SCREENSHOTS/3_bank_card.png")
    }
}

/** Play Store phone screenshots, relative to the module directory tests run in. */
private const val STORE_SCREENSHOTS = "../../fastlane/metadata/android/en-US/images/phoneScreenshots"
