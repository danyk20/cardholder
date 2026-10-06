package io.github.danyk20.cardholder.feature.barcodefullscreen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.danyk20.cardholder.core.designsystem.theme.CardholderTheme
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.testing.data.DemoCards
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the Play Store screenshot of a full-screen code (FLAG_SECURE blocks capturing it on a
 * device). Record with `./gradlew recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xxhdpi")
class StoreScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fullScreenCode() {
        val state = BarcodeUiState.Ready(
            title = DemoCards.grocer.title,
            shopName = "Green Grocer",
            code = DemoCards.grocerDetails.code,
            format = BarcodeFormat.EAN_13,
            isLocked = false,
        )
        composeRule.setContent {
            CardholderTheme(darkTheme = false, dynamicColor = false) {
                BarcodeScreen(state = state, onClose = {}, onOpenDetails = {}, onUnlock = {})
            }
        }
        composeRule.onRoot().captureRoboImage("$STORE_SCREENSHOTS/2_full_screen_code.png")
    }
}

/** Play Store phone screenshots, relative to the module directory tests run in. */
private const val STORE_SCREENSHOTS = "../../fastlane/metadata/android/en-US/images/phoneScreenshots"
