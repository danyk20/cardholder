package io.github.danyk20.cardholder.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.danyk20.cardholder.core.designsystem.theme.CardholderTheme
import io.github.danyk20.cardholder.core.model.UserPreferences
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the Play Store screenshot of the settings. Record with `./gradlew recordRoborazziDebug`. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xxhdpi")
class StoreScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settings() {
        composeRule.setContent {
            CardholderTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreen(
                    state = SettingsUiState(preferences = UserPreferences(useDynamicColor = false)),
                    onBack = {},
                    onThemeModeChange = {},
                    onDynamicColorChange = {},
                    onScreenshotsAllowedChange = {},
                    onExport = {},
                    onImport = {},
                    onResultShown = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$STORE_SCREENSHOTS/4_settings.png")
    }
}

/** Play Store phone screenshots, relative to the module directory tests run in. */
private const val STORE_SCREENSHOTS = "../../fastlane/metadata/android/en-US/images/phoneScreenshots"
