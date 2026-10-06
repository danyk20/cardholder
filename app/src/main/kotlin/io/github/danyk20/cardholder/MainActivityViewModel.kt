package io.github.danyk20.cardholder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.danyk20.cardholder.core.domain.repository.UserPreferencesRepository
import io.github.danyk20.cardholder.core.model.UserPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainActivityViewModel @Inject constructor(userPreferencesRepository: UserPreferencesRepository) : ViewModel() {
    val uiState: StateFlow<MainActivityUiState> = userPreferencesRepository.preferences
        .map(MainActivityUiState::Ready)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MainActivityUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

sealed interface MainActivityUiState {
    data object Loading : MainActivityUiState

    data class Ready(val preferences: UserPreferences) : MainActivityUiState
}
