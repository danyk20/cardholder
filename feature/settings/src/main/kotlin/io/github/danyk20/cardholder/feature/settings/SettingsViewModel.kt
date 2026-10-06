package io.github.danyk20.cardholder.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.danyk20.cardholder.core.domain.model.BackupResult
import io.github.danyk20.cardholder.core.domain.model.ImportStrategy
import io.github.danyk20.cardholder.core.domain.repository.BackupRepository
import io.github.danyk20.cardholder.core.domain.repository.UserPreferencesRepository
import io.github.danyk20.cardholder.core.domain.security.ScreenCapturePolicy
import io.github.danyk20.cardholder.core.model.ThemeMode
import io.github.danyk20.cardholder.core.model.UserPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val backupRepository: BackupRepository,
    private val screenCapturePolicy: ScreenCapturePolicy,
) : ViewModel() {
    private val backup = MutableStateFlow(BackupUiState())

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferences,
        backup,
        screenCapturePolicy.isAllowed,
    ) { prefs, backup, screenshotsAllowed ->
        SettingsUiState(preferences = prefs, backup = backup, screenshotsAllowed = screenshotsAllowed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun onDynamicColorChange(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDynamicColor(enabled) }
    }

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
        viewModelScope.launch {
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
)

enum class BackupOperation { EXPORT, IMPORT }

data class BackupUiState(
    /** The operation currently running, if any. */
    val inProgress: BackupOperation? = null,
    val result: BackupResult? = null,
)
