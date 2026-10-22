package io.github.danyk20.cardholder.feature.cardplaces

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.domain.location.AccessPoint
import io.github.danyk20.cardholder.core.domain.location.Bssid
import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.domain.location.LocationFix
import io.github.danyk20.cardholder.core.domain.location.LocationPermission
import io.github.danyk20.cardholder.core.domain.location.Place
import io.github.danyk20.cardholder.core.map.MapCamera
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.testing.MainDispatcherRule
import io.github.danyk20.cardholder.core.testing.data.TestCards
import io.github.danyk20.cardholder.core.testing.repository.FakeCardRepository
import io.github.danyk20.cardholder.core.testing.repository.TestPlaces
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class CardPlacesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val cards = FakeCardRepository()
    private val places = TestPlaces(cards)
    private val kaufland = TestCards.loyalty.copy(
        id = CardId("kaufland"),
        title = "Kaufland",
        suggestByLocation = true,
    )
    private val shop = GeoPoint(48.1486, 17.1077)
    private val corner = GeoPoint(48.1500, 17.1100)

    private fun place(
        id: Long,
        centre: GeoPoint? = shop,
        useCount: Int = 1,
        pinned: Boolean = false,
        card: CardId = kaufland.id,
        accessPoints: List<AccessPoint> = emptyList(),
    ) = Place(
        id = id,
        cardId = card,
        centre = centre,
        accuracyMeters = centre?.let { if (pinned) 25f else 10f },
        useCount = useCount,
        lastUsedAt = Instant.parse("2026-10-03T10:00:00Z"),
        accessPoints = accessPoints,
        isPinned = pinned,
        createdAt = Instant.parse("2026-10-01T10:00:00Z"),
        fixCount = if (pinned) 1 else 7,
    )

    private fun handle(): SavedStateHandle = SavedStateHandle(mapOf("cardId" to kaufland.id.value))

    private fun viewModel(handle: SavedStateHandle = handle()) = CardPlacesViewModel(
        savedStateHandle = handle,
        cardRepository = cards,
        cardPlaces = places.cardPlaces,
        surroundings = places.sensors,
        locationAccess = places.access,
    )

    /** Keeps the state hot, so the view-model's state reflects every change. */
    private fun TestScope.observe(viewModel: CardPlacesViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private fun TestScope.events(viewModel: CardPlacesViewModel): List<CardPlacesEvent> {
        val received = mutableListOf<CardPlacesEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            viewModel.uiEvents.collect { received += it }
        }
        return received
    }

    private fun addKaufland() = cards.add(kaufland, TestCards.loyaltyDetails)

    @Test
    fun `lists the card's places most used first, numbered, with Wi-Fi only places`() = runTest {
        addKaufland()
        places.places.add(place(1, centre = shop, useCount = 2))
        places.places.add(place(2, centre = null, useCount = 9, accessPoints = listOf(ap("Migros-Guest"))))
        val viewModel = viewModel()
        observe(viewModel)

        val items = viewModel.uiState.value.places

        assertEquals(listOf(2L, 1L), items.map { it.id })
        assertEquals(listOf(1, 2), items.map { it.number })
        assertNull(items[0].point)
        assertEquals(listOf("Migros-Guest"), items[0].wifiNames)
        assertEquals(shop, items[1].point)
        assertEquals(7, items[1].fixCount)
        assertEquals(10f, items[1].accuracyMeters)
    }

    @Test
    fun `places of other cards are not listed`() = runTest {
        addKaufland()
        places.places.add(place(1))
        places.places.add(place(2, card = TestCards.visa.id))
        val viewModel = viewModel()
        observe(viewModel)

        assertEquals(listOf(1L), viewModel.uiState.value.places.map { it.id })
    }

    @Test
    fun `only places with a position are drawn on the map`() = runTest {
        addKaufland()
        places.places.add(place(1))
        places.places.add(place(2, centre = null, accessPoints = listOf(ap("Shop"))))
        val viewModel = viewModel()
        observe(viewModel)

        assertEquals(listOf(1L), viewModel.uiState.value.markers.map { it.id })
    }

    @Test
    fun `the card's title and whether its suggestions are on come from the card`() = runTest {
        cards.add(kaufland.copy(suggestByLocation = false), TestCards.loyaltyDetails)
        val viewModel = viewModel()
        observe(viewModel)

        assertEquals("Kaufland", viewModel.uiState.value.title)
        assertFalse(viewModel.uiState.value.suggestionsOn)
    }

    @Test
    fun `selecting a place focuses the map on it, and selecting it again clears the selection`() = runTest {
        addKaufland()
        places.places.add(place(1))
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onPlaceSelected(1L)

        assertEquals(1L, viewModel.uiState.value.selectedId)
        assertEquals(shop, assertIs<MapCamera.Focus>(viewModel.uiState.value.camera).point)
        assertTrue(viewModel.uiState.value.markers.single().isSelected)

        viewModel.onPlaceSelected(1L)

        assertNull(viewModel.uiState.value.selectedId)
    }

    @Test
    fun `selecting a place without a position keeps the camera`() = runTest {
        addKaufland()
        places.places.add(place(1, centre = null, accessPoints = listOf(ap("Shop"))))
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onPlaceSelected(1L)

        assertEquals(1L, viewModel.uiState.value.selectedId)
        assertEquals(MapCamera.FitAll, viewModel.uiState.value.camera)
    }

    @Test
    fun `moving a place starts with no pin, the old spot faded, and a tap pins the new spot`() = runTest {
        addKaufland()
        places.places.add(place(1))
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onStartMove(1L)
        assertEquals(PlacesMode.Move(1L, pending = null), viewModel.uiState.value.mode)
        assertTrue(viewModel.uiState.value.markers.single().isFaded)

        viewModel.onMapTap(corner)

        assertEquals(PlacesMode.Move(1L, pending = corner), viewModel.uiState.value.mode)
        assertEquals(corner, viewModel.uiState.value.editPin)
    }

    @Test
    fun `saving a move moves the place, pins it, leaves move mode and shows a message`() = runTest {
        addKaufland()
        places.places.add(place(1))
        val viewModel = viewModel()
        observe(viewModel)
        val received = events(viewModel)

        viewModel.onStartMove(1L)
        viewModel.onMapTap(corner)
        viewModel.onSaveEdit()

        val moved = places.places.places.value.single()
        assertEquals(corner.latitude, moved.centre?.latitude ?: error("no centre"), 1e-9)
        assertTrue(moved.isPinned)
        assertEquals(PlacesMode.Browse, viewModel.uiState.value.mode)
        assertEquals(1L, viewModel.uiState.value.selectedId)
        assertEquals(listOf(CardPlacesEvent.Show(PlacesMessage.MOVED)), received)
    }

    @Test
    fun `cancelling a move leaves the place where it was`() = runTest {
        addKaufland()
        places.places.add(place(1))
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onStartMove(1L)
        viewModel.onMapTap(corner)
        viewModel.onCancelEdit()

        assertEquals(PlacesMode.Browse, viewModel.uiState.value.mode)
        assertEquals(shop, places.places.places.value.single().centre)
        assertFalse(places.places.places.value.single().isPinned)
    }

    @Test
    fun `saving a move without a spot on the map changes nothing`() = runTest {
        addKaufland()
        places.places.add(place(1))
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onStartMove(1L)
        viewModel.onSaveEdit()

        assertEquals(PlacesMode.Move(1L, pending = null), viewModel.uiState.value.mode)
        assertEquals(shop, places.places.places.value.single().centre)
    }

    @Test
    fun `a spot that isn't on the map is not saved and says so`() = runTest {
        addKaufland()
        places.places.add(place(1))
        val viewModel = viewModel()
        observe(viewModel)
        val received = events(viewModel)

        viewModel.onStartMove(1L)
        viewModel.onMapTap(GeoPoint(86.0, 17.0))
        viewModel.onSaveEdit()

        assertEquals(shop, places.places.places.value.single().centre)
        assertEquals(listOf(CardPlacesEvent.Show(PlacesMessage.NOT_SAVED)), received)
    }

    @Test
    fun `moving a place that was deleted meanwhile says it couldn't be saved`() = runTest {
        addKaufland()
        places.places.add(place(1))
        val viewModel = viewModel()
        observe(viewModel)
        val received = events(viewModel)

        viewModel.onStartMove(1L)
        viewModel.onMapTap(corner)
        places.places.forget(1L)
        viewModel.onSaveEdit()

        assertEquals(listOf(CardPlacesEvent.Show(PlacesMessage.NOT_SAVED)), received)
        assertEquals(PlacesMode.Browse, viewModel.uiState.value.mode)
    }

    @Test
    fun `adding a place saves it at the tapped spot, pinned, and selects it`() = runTest {
        addKaufland()
        val viewModel = viewModel()
        observe(viewModel)
        val received = events(viewModel)

        viewModel.onStartAdd()
        viewModel.onMapTap(corner)
        viewModel.onSaveEdit()

        val added = places.places.places.value.single()
        assertEquals(kaufland.id, added.cardId)
        assertTrue(added.isPinned)
        assertEquals(corner.latitude, added.centre?.latitude ?: error("no centre"), 1e-9)
        assertEquals(added.id, viewModel.uiState.value.selectedId)
        assertEquals(PlacesMode.Browse, viewModel.uiState.value.mode)
        assertEquals(listOf(CardPlacesEvent.Show(PlacesMessage.ADDED)), received)
    }

    @Test
    fun `adding a place off the map is refused`() = runTest {
        addKaufland()
        val viewModel = viewModel()
        observe(viewModel)
        val received = events(viewModel)

        viewModel.onStartAdd()
        viewModel.onMapTap(GeoPoint(Double.NaN, 17.0))
        viewModel.onSaveEdit()

        assertTrue(places.places.places.value.isEmpty())
        assertEquals(listOf(CardPlacesEvent.Show(PlacesMessage.NOT_SAVED)), received)
    }

    @Test
    fun `cancelling an addition saves nothing`() = runTest {
        addKaufland()
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onStartAdd()
        viewModel.onMapTap(corner)
        viewModel.onCancelEdit()

        assertTrue(places.places.places.value.isEmpty())
        assertEquals(PlacesMode.Browse, viewModel.uiState.value.mode)
    }

    @Test
    fun `deleting asks first and removes the place once confirmed`() = runTest {
        addKaufland()
        places.places.add(place(1))
        places.places.add(place(2, centre = corner))
        val viewModel = viewModel()
        observe(viewModel)
        val received = events(viewModel)

        viewModel.onRequestDelete(1L)
        assertEquals(1L, viewModel.uiState.value.pendingDelete)
        viewModel.onCancelDelete()
        assertNull(viewModel.uiState.value.pendingDelete)
        assertEquals(2, places.places.places.value.size)

        viewModel.onRequestDelete(1L)
        viewModel.onConfirmDelete()

        assertEquals(listOf(2L), places.places.places.value.map { it.id })
        assertNull(viewModel.uiState.value.pendingDelete)
        assertEquals(listOf(CardPlacesEvent.Show(PlacesMessage.DELETED)), received)
    }

    @Test
    fun `deleting the selected place clears the selection`() = runTest {
        addKaufland()
        places.places.add(place(1))
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onPlaceSelected(1L)
        viewModel.onRequestDelete(1L)
        viewModel.onConfirmDelete()

        assertNull(viewModel.uiState.value.selectedId)
    }

    @Test
    fun `forgetting all places asks first and removes only this card's places`() = runTest {
        addKaufland()
        places.places.add(place(1))
        places.places.add(place(2, centre = corner))
        places.places.add(place(3, card = TestCards.visa.id))
        val viewModel = viewModel()
        observe(viewModel)
        val received = events(viewModel)

        viewModel.onRequestForgetAll()
        assertTrue(viewModel.uiState.value.confirmForgetAll)
        viewModel.onCancelForgetAll()
        assertFalse(viewModel.uiState.value.confirmForgetAll)
        assertEquals(3, places.places.places.value.size)

        viewModel.onRequestForgetAll()
        viewModel.onConfirmForgetAll()

        assertEquals(listOf(3L), places.places.places.value.map { it.id })
        assertEquals(listOf(CardPlacesEvent.Show(PlacesMessage.FORGOTTEN)), received)
    }

    @Test
    fun `deleting the card closes the screen`() = runTest {
        addKaufland()
        places.places.add(place(1))
        val viewModel = viewModel()
        observe(viewModel)
        val received = events(viewModel)

        cards.delete(kaufland.id)

        assertEquals(listOf(CardPlacesEvent.Close), received)
    }

    @Test
    fun `a card that is already gone closes the screen`() = runTest {
        val viewModel = viewModel()
        observe(viewModel)
        val received = events(viewModel)

        assertEquals(listOf(CardPlacesEvent.Close), received)
    }

    @Test
    fun `a move keeps its pending pin when the screen is recreated`() = runTest {
        addKaufland()
        places.places.add(place(1))
        val saved = handle()
        val first = viewModel(saved)
        observe(first)

        first.onStartMove(1L)
        first.onMapTap(corner)

        val recreated = viewModel(saved)
        observe(recreated)
        assertEquals(PlacesMode.Move(1L, pending = corner), recreated.uiState.value.mode)
    }

    @Test
    fun `an addition restored from the saved state keeps its pending pin`() = runTest {
        addKaufland()
        val restored = SavedStateHandle(
            mapOf(
                "cardId" to kaufland.id.value,
                "editing" to true,
                "editingPlaceId" to null,
                "pinLatitude" to corner.latitude,
                "pinLongitude" to corner.longitude,
            ),
        )
        val viewModel = viewModel(restored)
        observe(viewModel)

        assertEquals(PlacesMode.Add(pending = corner), viewModel.uiState.value.mode)
    }

    @Test
    fun `leaving the edit mode forgets the saved pin`() = runTest {
        addKaufland()
        places.places.add(place(1))
        val saved = handle()
        val first = viewModel(saved)
        observe(first)

        first.onStartMove(1L)
        first.onMapTap(corner)
        first.onCancelEdit()

        val recreated = viewModel(saved)
        observe(recreated)
        assertEquals(PlacesMode.Browse, recreated.uiState.value.mode)
    }

    @Test
    fun `centre on me focuses the fix when a location permission is granted`() = runTest {
        addKaufland()
        places.sensors.fix = LocationFix(corner, 12f, Instant.parse("2026-10-18T12:00:00Z"))
        places.access.permission = LocationPermission.PRECISE
        val viewModel = viewModel()
        observe(viewModel)

        assertTrue(viewModel.uiState.value.canCentreOnMe)
        viewModel.onCentreOnMe()

        assertEquals(corner, assertIs<MapCamera.Focus>(viewModel.uiState.value.camera).point)
    }

    @Test
    fun `centre on me does nothing without a location permission`() = runTest {
        addKaufland()
        places.sensors.fix = LocationFix(corner, 12f, Instant.parse("2026-10-18T12:00:00Z"))
        places.access.permission = LocationPermission.NONE
        val viewModel = viewModel()
        observe(viewModel)

        assertFalse(viewModel.uiState.value.canCentreOnMe)
        viewModel.onCentreOnMe()

        assertEquals(MapCamera.FitAll, viewModel.uiState.value.camera)
        assertEquals(0, places.sensors.fixReads)
    }

    @Test
    fun `an unreachable map is reported in the state`() = runTest {
        addKaufland()
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onTilesUnreachable(true)
        assertTrue(viewModel.uiState.value.tilesUnreachable)
        viewModel.onTilesUnreachable(false)
        assertFalse(viewModel.uiState.value.tilesUnreachable)
    }

    private fun ap(ssid: String) = AccessPoint(Bssid(ssid.hashCode().toLong()), ssid, -60)
}
