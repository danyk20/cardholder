package io.github.danyk20.cardholder.feature.cardplaces

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.map.MapCamera
import io.github.danyk20.cardholder.core.map.MapMarker
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardPlacesScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val shop = GeoPoint(48.1486, 17.1077)
    private val corner = GeoPoint(48.1500, 17.1100)

    private val learned = PlaceItem(
        id = 1,
        number = 1,
        point = shop,
        accuracyMeters = 10f,
        isPinned = false,
        fixCount = 7,
        useCount = 12,
        lastUsedAt = Instant.parse("2026-10-03T10:00:00Z"),
        wifiNames = listOf("Migros-Guest"),
    )
    private val pinned = learned.copy(
        id = 2,
        number = 2,
        point = corner,
        accuracyMeters = 25f,
        isPinned = true,
        fixCount = 1,
        useCount = 3,
        wifiNames = emptyList(),
    )
    private val wifiOnly = learned.copy(
        id = 3,
        number = 3,
        point = null,
        accuracyMeters = null,
        wifiNames = listOf("Shop-WiFi"),
    )

    /** A map that shows what it was given, and reports taps and marker taps like the real one. */
    private val fakeMap: PlacesMapSlot = {
            markers: List<MapMarker>,
            _: GeoPoint?,
            _: MapCamera,
            onMapTap: (GeoPoint) -> Unit,
            onMarkerTap: (Long) -> Unit,
            _: (Boolean) -> Unit,
        ->
        Column {
            Text("fake map with ${markers.size} markers")
            Button(onClick = { onMapTap(corner) }) { Text("tap map") }
            markers.forEach { marker -> Button(onClick = { onMarkerTap(marker.id) }) { Text("marker ${marker.id}") } }
        }
    }

    private fun show(
        state: CardPlacesUiState,
        actions: CardPlacesActions = CardPlacesActions(),
        onBack: () -> Unit = {},
    ) = composeRule.setContent {
        CardPlacesScreen(
            state = state,
            events = emptyFlow(),
            actions = actions,
            onBack = onBack,
            map = fakeMap,
        )
    }

    @Test
    fun `the title names the card`() {
        show(CardPlacesUiState(title = "Kaufland"))

        composeRule.onNodeWithText("Places · Kaufland").assertIsDisplayed()
    }

    @Test
    fun `rows say how a place was learned, how often it was used and its Wi-Fi`() {
        show(CardPlacesUiState(title = "Kaufland", places = listOf(learned, pinned)))

        composeRule.onNodeWithText("Learned from 7 GPS readings · ±10 m").assertIsDisplayed()
        composeRule.onNodeWithText("Set by you").assertIsDisplayed()
        composeRule.onNodeWithText("Used 12 times, last", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Wi‑Fi: Migros-Guest").assertIsDisplayed()
    }

    @Test
    fun `a place known only from its Wi-Fi says it has no map position`() {
        show(CardPlacesUiState(title = "Kaufland", places = listOf(wifiOnly)))

        composeRule.onNodeWithText("Wi‑Fi only, no map position").assertIsDisplayed()
    }

    @Test
    fun `tapping a place expands its Move and Delete actions`() {
        var moved = 0
        var deleted = 0
        show(
            CardPlacesUiState(title = "Kaufland", places = listOf(learned), selectedId = 1),
            CardPlacesActions(onStartMove = { moved = it.toInt() }, onRequestDelete = { deleted = it.toInt() }),
        )

        composeRule.onNodeWithText("Move").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(1, moved)
        assertEquals(1, deleted)
    }

    @Test
    fun `a place with no map position offers to set it on the map`() {
        var moved = -1L
        show(
            CardPlacesUiState(title = "Kaufland", places = listOf(wifiOnly), selectedId = 3),
            CardPlacesActions(onStartMove = { moved = it }),
        )

        composeRule.onNodeWithText("Set on map").performClick()

        assertEquals(3L, moved)
    }

    @Test
    fun `tapping a row selects its place`() {
        var selected = -1L
        show(
            CardPlacesUiState(title = "Kaufland", places = listOf(learned)),
            CardPlacesActions(onPlaceSelected = { selected = it }),
        )

        composeRule.onNodeWithText("Learned from 7 GPS readings · ±10 m").performClick()

        assertEquals(1L, selected)
    }

    @Test
    fun `the map reports taps and marker taps`() {
        var tapped: GeoPoint? = null
        var marked = -1L
        show(
            CardPlacesUiState(title = "Kaufland", places = listOf(learned)),
            CardPlacesActions(onMapTap = { tapped = it }, onPlaceSelected = { marked = it }),
        )

        composeRule.onNodeWithText("tap map").performClick()
        composeRule.onNodeWithText("marker 1").performClick()

        assertEquals(corner, tapped)
        assertEquals(1L, marked)
    }

    @Test
    fun `moving shows the edit bar whose Save waits for a spot on the map`() {
        var saved = false
        var cancelled = false
        show(
            CardPlacesUiState(title = "Kaufland", places = listOf(learned), mode = PlacesMode.Move(1, pending = null)),
            CardPlacesActions(onSaveEdit = { saved = true }, onCancelEdit = { cancelled = true }),
        )

        composeRule.onNodeWithText("Tap the correct spot on the map.").assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").performClick()
        assertTrue(cancelled)
        assertTrue(!saved)
    }

    @Test
    fun `with a spot placed, Save is enabled and saves`() {
        var saved = false
        show(
            CardPlacesUiState(
                title = "Kaufland",
                places = listOf(learned),
                mode = PlacesMode.Move(1, pending = corner),
            ),
            CardPlacesActions(onSaveEdit = { saved = true }),
        )

        composeRule.onNodeWithText("Save").assertIsEnabled().performClick()

        assertTrue(saved)
    }

    @Test
    fun `while editing there is no add button, and Back cancels the edit instead of leaving`() {
        var cancelled = false
        var left = false
        show(
            CardPlacesUiState(title = "Kaufland", places = listOf(learned), mode = PlacesMode.Add(pending = null)),
            CardPlacesActions(onCancelEdit = { cancelled = true }),
            onBack = { left = true },
        )

        composeRule.onAllNodesWithText("Add place").assertCountEquals(0)
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertTrue(cancelled)
        assertTrue(!left)
    }

    @Test
    fun `Back leaves the screen when nothing is being edited`() {
        var left = false
        show(CardPlacesUiState(title = "Kaufland", places = listOf(learned)), onBack = { left = true })

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertTrue(left)
    }

    @Test
    fun `adding starts with the add button and says where to tap when there is no pin yet`() {
        var started = false
        show(
            CardPlacesUiState(title = "Kaufland", places = listOf(learned)),
            CardPlacesActions(onStartAdd = { started = true }),
        )

        composeRule.onNode(hasText("Add place"), useUnmergedTree = true).performClick()

        assertTrue(started)
    }

    @Test
    fun `adding for a card without suggestions says they need turning on`() {
        show(CardPlacesUiState(title = "Kaufland", mode = PlacesMode.Add(pending = corner), suggestionsOn = false))

        composeRule.onNodeWithText("Turn on suggestions for this card to use this place.").assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun `adding for a card with suggestions on shows no such hint`() {
        show(CardPlacesUiState(title = "Kaufland", mode = PlacesMode.Add(pending = null), suggestionsOn = true))

        composeRule.onAllNodesWithText("Turn on suggestions for this card to use this place.").assertCountEquals(0)
    }

    @Test
    fun `deleting asks in a dialog and deletes only when confirmed`() {
        var deleted = false
        show(
            CardPlacesUiState(title = "Kaufland", places = listOf(learned), pendingDelete = 1),
            CardPlacesActions(onConfirmDelete = { deleted = true }),
        )

        composeRule.onNodeWithText("Delete this place?").assertIsDisplayed()
        composeRule.onNodeWithText("Cardholder may learn it again when you use the card here.").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()

        assertTrue(deleted)
    }

    @Test
    fun `forgetting all places is in the menu and asks in a dialog first`() {
        var requested = false
        var confirmed = false
        show(
            CardPlacesUiState(title = "Kaufland", places = listOf(learned)),
            CardPlacesActions(onRequestForgetAll = { requested = true }, onConfirmForgetAll = { confirmed = true }),
        )

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Forget all places").performClick()

        assertTrue(requested)
        assertTrue(!confirmed)
    }

    @Test
    fun `the forget-all dialog forgets when confirmed`() {
        var confirmed = false
        show(
            CardPlacesUiState(title = "Kaufland", places = listOf(learned), confirmForgetAll = true),
            CardPlacesActions(onConfirmForgetAll = { confirmed = true }),
        )

        composeRule.onNodeWithText("Forget all places?").assertIsDisplayed()
        composeRule.onNodeWithText("Forget").performClick()

        assertTrue(confirmed)
    }

    @Test
    fun `centre on me is offered only with a location permission`() {
        var centred = false
        show(
            CardPlacesUiState(title = "Kaufland", canCentreOnMe = true),
            CardPlacesActions(onCentreOnMe = { centred = true }),
        )

        composeRule.onNodeWithContentDescription("Centre on me").performClick()

        assertTrue(centred)
    }

    @Test
    fun `without a location permission there is no centre on me button`() {
        show(CardPlacesUiState(title = "Kaufland", canCentreOnMe = false))

        composeRule.onAllNodesWithContentDescription("Centre on me").assertCountEquals(0)
    }

    @Test
    fun `an offline map says so, and the list still shows the places`() {
        show(CardPlacesUiState(title = "Kaufland", places = listOf(learned), tilesUnreachable = true))

        composeRule.onNodeWithText(
            "The map can't load offline. Your places are still listed below.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Learned from 7 GPS readings · ±10 m").assertIsDisplayed()
    }

    @Test
    fun `without places the list says how to get one`() {
        show(CardPlacesUiState(title = "Kaufland"))

        composeRule.onNodeWithText(
            "No places yet. A place is learned when you use this card, or you can add one on the map.",
        )
            .assertIsDisplayed()
    }

    @Test
    fun `the privacy note under the map names OpenStreetMap`() {
        show(CardPlacesUiState(title = "Kaufland"))

        composeRule.onNodeWithText("Map © OpenStreetMap", substring = true).assertIsDisplayed()
    }
}
