package io.github.danyk20.cardholder.feature.cardplaces

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.danyk20.cardholder.core.designsystem.theme.CardholderTheme
import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.map.MapCamera
import java.time.Instant
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the Places screen with a stand-in for the map, so the screenshot doesn't depend on the tiles.
 * Record with `./gradlew recordRoborazziDebug`, verify with `verifyRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xxhdpi")
class CardPlacesScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val shop = GeoPoint(48.1486, 17.1077)

    private val places = listOf(
        PlaceItem(
            id = 1,
            number = 1,
            point = shop,
            accuracyMeters = 10f,
            isPinned = false,
            fixCount = 7,
            useCount = 12,
            lastUsedAt = Instant.parse("2026-10-03T10:00:00Z"),
            wifiNames = listOf("Migros-Guest"),
            hasWifi = true,
        ),
        PlaceItem(
            id = 2,
            number = 2,
            point = GeoPoint(48.1500, 17.1100),
            accuracyMeters = 25f,
            isPinned = true,
            fixCount = 1,
            useCount = 3,
            lastUsedAt = Instant.parse("2026-10-10T10:00:00Z"),
            wifiNames = emptyList(),
        ),
        PlaceItem(
            id = 3,
            number = 3,
            point = null,
            accuracyMeters = null,
            isPinned = false,
            fixCount = 0,
            useCount = 1,
            lastUsedAt = Instant.parse("2026-09-20T10:00:00Z"),
            wifiNames = listOf("Shop-WiFi"),
            hasWifi = true,
        ),
    )

    /** A grey panel where the map would be, with the markers' count, so the screenshot shows the layout. */
    private val stillMap: PlacesMapSlot = { markers, _, _, _, _, _ ->
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("Map with ${markers.size} places")
        }
    }

    @Test
    fun placesScreen() = capture(
        CardPlacesUiState(
            title = "Kaufland",
            places = places,
            selectedId = 1,
            camera = MapCamera.FitAll,
            suggestionsOn = true,
            canCentreOnMe = true,
        ),
        name = "card_places",
    )

    @Test
    fun placesScreenMoving() = capture(
        CardPlacesUiState(
            title = "Kaufland",
            places = places,
            mode = PlacesMode.Move(placeId = 1, pending = GeoPoint(48.1490, 17.1080)),
            suggestionsOn = true,
        ),
        name = "card_places_moving",
    )

    private fun capture(state: CardPlacesUiState, name: String) {
        composeRule.setContent {
            CardholderTheme(darkTheme = false, dynamicColor = false) {
                CardPlacesScreen(
                    state = state,
                    events = emptyFlow(),
                    actions = CardPlacesActions(),
                    onBack = {},
                    map = stillMap,
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
