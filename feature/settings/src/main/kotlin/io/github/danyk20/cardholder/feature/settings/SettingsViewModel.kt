package io.github.danyk20.cardholder.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.danyk20.cardholder.core.domain.location.LocationAccess
import io.github.danyk20.cardholder.core.domain.model.BackupResult
import io.github.danyk20.cardholder.core.domain.model.ImportStrategy
import io.github.danyk20.cardholder.core.domain.repository.AppDataRepository
import io.github.danyk20.cardholder.core.domain.repository.BackupRepository
import io.github.danyk20.cardholder.core.domain.repository.PlaceRepository
import io.github.danyk20.cardholder.core.domain.repository.UserPreferencesRepository
import io.github.danyk20.cardholder.core.domain.security.ScreenCapturePolicy
import io.github.danyk20.cardholder.core.domain.usecase.FindNearbyCardsUseCase
import io.github.danyk20.cardholder.core.model.PlaceSuggestions
import io.github.danyk20.cardholder.core.model.ThemeMode
import io.github.danyk20.cardholder.core.model.UserPreferences
import io.github.danyk20.cardholder.core.ui.launchSafely
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val backupRepository: BackupRepository,
    private val screenCapturePolicy: ScreenCapturePolicy,
    private val appDataRepository: AppDataRepository,
    private val placeRepository: PlaceRepository,
    private val locationAccess: LocationAccess,
    private val findNearbyCards: FindNearbyCardsUseCase,
) : ViewModel() {
    private val backup = MutableStateFlow(BackupUiState())
    private val location = MutableStateFlow(readLocation())

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferences,
        backup,
        screenCapturePolicy.isAllowed,
        location,
        placeRepository.observeTotal(),
    ) { prefs, backup, screenshotsAllowed, location, placeCount ->
        SettingsUiState(
            preferences = prefs,
            backup = backup,
            screenshotsAllowed = screenshotsAllowed,
            location = location.copy(placeCount = placeCount),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    /** Picks up permissions and the Location switch changed in the system settings. */
    fun onResume() = location.update { readLocation(message = it.message) }

    /** Applies a mode whose permissions were granted. */
    fun onPlaceSuggestionsChange(mode: PlaceSuggestions) {
        onResume()
        launchSafely {
            preferencesRepository.setPlaceSuggestions(mode)
            findNearbyCards.invalidate()
        }
    }

    fun onForgetAllPlaces() {
        launchSafely {
            placeRepository.forgetAll()
            findNearbyCards.invalidate()
            location.update { it.copy(message = LocationMessage.PLACES_FORGOTTEN) }
        }
    }

    fun onLocationMessage(message: LocationMessage) = location.update { it.copy(message = message) }

    fun onLocationMessageShown() = location.update { it.copy(message = null) }

    private fun readLocation(message: LocationMessage? = null) = LocationSettingsUiState(
        permission = locationAccess.permission(),
        isLocationEnabled = locationAccess.isLocationEnabled(),
        isArrivalSupported = locationAccess.isArrivalSupported(),
        message = message,
    )

    fun onThemeModeChange(mode: ThemeMode) {
        launchSafely { preferencesRepository.setThemeMode(mode) }
    }

    fun onExpiryRemindersChange(enabled: Boolean) {
        launchSafely { preferencesRepository.setExpiryReminders(enabled) }
    }

    fun onDynamicColorChange(enabled: Boolean) {
        launchSafely { preferencesRepository.setDynamicColor(enabled) }
    }

    /** Permanently erases everything; the system closes the app afterwards. */
    fun onEraseAllData() = appDataRepository.eraseAllData()

    /** Allows screenshots of card data until the app leaves the screen. */
    fun onScreenshotsAllowedChange(allowed: Boolean) = screenCapturePolicy.setAllowed(allowed)

    /** Called once the user chose a password, a destination and authenticated. */
    fun export(destinationUri: String, password: CharArray) = runBackup(BackupOperation.EXPORT, password) {
        backupRepository.export(destinationUri, password)
    }

    fun import(sourceUri: String, password: CharArray, replaceExisting: Boolean) =
        runBackup(BackupOperation.IMPORT, password) {
            val strategy = if (replaceExisting) ImportStrategy.REPLACE_EXISTING else ImportStrategy.SKIP_EXISTING
            backupRepository.import(sourceUri, password, strategy)
        }

    fun onResultShown() = backup.update { it.copy(result = null) }

    /** Runs one backup operation at a time and wipes [password] afterwards. */
    private fun runBackup(operation: BackupOperation, password: CharArray, block: suspend () -> BackupResult) {
        if (backup.value.inProgress != null) {
            password.fill(' ')
            return
        }
        backup.update { it.copy(inProgress = operation, result = null) }
        launchSafely(onError = { backup.value = BackupUiState(result = BackupResult.Failed(it)) }) {
            val result = try {
                block()
            } finally {
                password.fill(' ')
            }
            backup.update { BackupUiState(inProgress = null, result = result) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val backup: BackupUiState = BackupUiState(),
    val screenshotsAllowed: Boolean = false,
    val location: LocationSettingsUiState = LocationSettingsUiState(),
)

enum class BackupOperation { EXPORT, IMPORT }

data class BackupUiState(
    /** The operation currently running, if any. */
    val inProgress: BackupOperation? = null,
    val result: BackupResult? = null,
)
