package io.github.danyk20.cardholder.feature.settings

import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.domain.location.LocationPermission
import io.github.danyk20.cardholder.core.domain.location.Place
import io.github.danyk20.cardholder.core.domain.model.BackupResult
import io.github.danyk20.cardholder.core.domain.model.ImportStrategy
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.PlaceSuggestions
import io.github.danyk20.cardholder.core.model.ThemeMode
import io.github.danyk20.cardholder.core.testing.MainDispatcherRule
import io.github.danyk20.cardholder.core.testing.repository.FakeAppDataRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeBackupRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeCardRepository
import io.github.danyk20.cardholder.core.testing.repository.FakeScreenCapturePolicy
import io.github.danyk20.cardholder.core.testing.repository.FakeUserPreferencesRepository
import io.github.danyk20.cardholder.core.testing.repository.TestPlaces
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = FakeUserPreferencesRepository()
    private val backups = FakeBackupRepository()
    private val screenCapture = FakeScreenCapturePolicy()
    private val appData = FakeAppDataRepository()
    private val places = TestPlaces(FakeCardRepository(), preferences)
    private val viewModel by lazy {
        SettingsViewModel(
            preferencesRepository = preferences,
            backupRepository = backups,
            screenCapturePolicy = screenCapture,
            appDataRepository = appData,
            placeRepository = places.places,
            locationAccess = places.access,
            findNearbyCards = places.findNearbyCards,
        )
    }

    private fun TestScope.observeState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }

    @Test
    fun `changes the suggestion mode`() = runTest {
        observeState()

        viewModel.onPlaceSuggestionsChange(PlaceSuggestions.ARRIVAL)

        assertEquals(PlaceSuggestions.ARRIVAL, viewModel.uiState.value.preferences.placeSuggestions)
        assertEquals(LocationPermission.PRECISE, viewModel.uiState.value.location.permission)
    }

    @Test
    fun `forgets all places`() = runTest {
        observeState()
        places.places.add(
            Place(1, CardId("card"), GeoPoint(48.0, 17.0), 10f, 1, Instant.parse("2026-10-01T10:00:00Z")),
        )
        assertEquals(1, viewModel.uiState.value.location.placeCount)

        viewModel.onForgetAllPlaces()

        assertEquals(0, viewModel.uiState.value.location.placeCount)
        assertEquals(LocationMessage.PLACES_FORGOTTEN, viewModel.uiState.value.location.message)
    }

    @Test
    fun `picks up permissions changed in the system settings`() = runTest {
        observeState()
        places.access.permission = LocationPermission.PRECISE_ALWAYS
        places.access.arrivalSupported = false

        viewModel.onResume()

        assertEquals(LocationPermission.PRECISE_ALWAYS, viewModel.uiState.value.location.permission)
        assertEquals(false, viewModel.uiState.value.location.isArrivalSupported)
    }

    @Test
    fun `changes appearance preferences`() = runTest {
        observeState()

        viewModel.onThemeModeChange(ThemeMode.DARK)
        viewModel.onDynamicColorChange(false)

        assertEquals(ThemeMode.DARK, viewModel.uiState.value.preferences.themeMode)
        assertEquals(false, viewModel.uiState.value.preferences.useDynamicColor)
    }

    @Test
    fun `screenshots are not allowed by default`() = runTest {
        observeState()

        assertFalse(viewModel.uiState.value.screenshotsAllowed)
    }

    @Test
    fun `toggles the temporary screenshot permission`() = runTest {
        observeState()

        viewModel.onScreenshotsAllowedChange(true)
        assertTrue(screenCapture.isAllowed.value)
        assertTrue(viewModel.uiState.value.screenshotsAllowed)

        // Revoked elsewhere, e.g. when the app leaves the screen.
        screenCapture.setAllowed(false)
        assertFalse(viewModel.uiState.value.screenshotsAllowed)
    }

    @Test
    fun `erases all data on request`() {
        viewModel.onEraseAllData()

        assertTrue(appData.erased)
    }

    @Test
    fun `export reports the result and wipes the password`() = runTest {
        observeState()
        val password = "a good password".toCharArray()

        viewModel.export("content://backup", password)

        assertEquals(BackupResult.Exported(2), viewModel.uiState.value.backup.result)
        assertEquals("content://backup" to "a good password", backups.exports.single())
        assertTrue(password.all { it == ' ' })

        viewModel.onResultShown()
        assertNull(viewModel.uiState.value.backup.result)
    }

    @Test
    fun `import passes the chosen strategy`() = runTest {
        viewModel.import("content://backup", "pw".toCharArray(), replaceExisting = true)

        assertEquals(ImportStrategy.REPLACE_EXISTING, backups.imports.single())
    }
}
