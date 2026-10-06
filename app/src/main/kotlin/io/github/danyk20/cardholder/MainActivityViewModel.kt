package io.github.danyk20.cardholder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.danyk20.cardholder.core.domain.repository.AppDataRepository
import io.github.danyk20.cardholder.core.domain.repository.UserPreferencesRepository
import io.github.danyk20.cardholder.core.model.UserPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    userPreferencesRepository: UserPreferencesRepository,
    private val appDataRepository: AppDataRepository,
) : ViewModel() {
    /** Opened once at start; if the data can't be decrypted, the app offers to start over instead of crashing. */
    private val storageAvailable = flow { emit(appDataRepository.isStorageAvailable()) }

    val uiState: StateFlow<MainActivityUiState> =
        combine(storageAvailable, userPreferencesRepository.preferences) { available, preferences ->
            if (available) {
                MainActivityUiState.Ready(
                    preferences,
                )
            } else {
                MainActivityUiState.StorageUnavailable(preferences)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MainActivityUiState.Loading)

    fun onEraseAllData() = appDataRepository.eraseAllData()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

sealed interface MainActivityUiState {
    data object Loading : MainActivityUiState

    data class Ready(val preferences: UserPreferences) : MainActivityUiState

    /** The encrypted cards can't be opened on this device any more; see [StorageErrorScreen]. */
    data class StorageUnavailable(val preferences: UserPreferences) : MainActivityUiState
}
