package io.github.danyk20.cardholder.feature.settings.sharedimport

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.danyk20.cardholder.core.domain.model.BackupResult
import io.github.danyk20.cardholder.core.domain.model.SharedCardAction
import io.github.danyk20.cardholder.core.domain.model.SharedCardChoice
import io.github.danyk20.cardholder.core.domain.model.SharedCardPreview
import io.github.danyk20.cardholder.core.domain.model.SharedFilePreview
import io.github.danyk20.cardholder.core.domain.repository.BackupRepository
import io.github.danyk20.cardholder.core.ui.launchSafely
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Imports a file another app opened Cardholder with: asks for its password if needed, then lets the
 * receiver decide per card whether to add, replace or skip it and whether to lock it.
 */
@HiltViewModel
class SharedImportViewModel @Inject constructor(private val backupRepository: BackupRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<SharedImportUiState>(SharedImportUiState.Loading)
    val uiState: StateFlow<SharedImportUiState> = _uiState.asStateFlow()

    private var uri: String? = null

    /** Kept only in memory, until the import finished or was cancelled. */
    private var password: CharArray? = null

    fun open(uri: String) {
        if (this.uri == uri) return
        this.uri = uri
        clearPassword()
        loadPreview()
    }

    fun onPassword(password: CharArray) {
        clearPassword()
        this.password = password
        loadPreview()
    }

    fun onActionChange(key: String, action: SharedCardAction) = updateCard(key) { it.copy(action = action) }

    fun onLockedChange(key: String, locked: Boolean) = updateCard(key) { it.copy(locked = locked) }

    fun onImport() {
        val state = _uiState.value as? SharedImportUiState.Choosing ?: return
        val uri = uri ?: return
        _uiState.value = SharedImportUiState.Loading
        launchSafely(onError = { finish(BackupResult.Failed(it)) }) {
            val choices = state.cards.associate { it.preview.key to SharedCardChoice(it.action, it.locked) }
            finish(backupRepository.importShared(uri, password, choices))
        }
    }

    fun onCancel() {
        clearPassword()
        uri = null
        _uiState.value = SharedImportUiState.Closed
    }

    private fun loadPreview() {
        val uri = uri ?: return
        _uiState.value = SharedImportUiState.Loading
        launchSafely(onError = { finish(BackupResult.Failed(it)) }) {
            _uiState.value = when (val preview = backupRepository.preview(uri, password)) {
                is SharedFilePreview.Cards -> SharedImportUiState.Choosing(
                    preview.cards.map { card ->
                        SharedCardUi(
                            preview = card,
                            // Duplicates are skipped unless the receiver decides otherwise.
                            action = if (card.duplicateOf == null) SharedCardAction.ADD_NEW else SharedCardAction.SKIP,
                            locked = card.wasLocked,
                        )
                    },
                )

                is SharedFilePreview.Unreadable -> when (preview.result) {
                    BackupResult.PasswordRequired -> SharedImportUiState.PasswordNeeded(wrongPassword = false)

                    BackupResult.WrongPassword -> SharedImportUiState.PasswordNeeded(wrongPassword = true)

                    else -> {
                        clearPassword()
                        SharedImportUiState.Done(preview.result)
                    }
                }
            }
        }
    }

    private fun finish(result: BackupResult) {
        clearPassword()
        _uiState.value = SharedImportUiState.Done(result)
    }

    private fun updateCard(key: String, change: (SharedCardUi) -> SharedCardUi) = _uiState.update { state ->
        if (state !is SharedImportUiState.Choosing) return@update state
        state.copy(cards = state.cards.map { if (it.preview.key == key) change(it) else it })
    }

    private fun clearPassword() {
        password?.fill(' ')
        password = null
    }

    override fun onCleared() = clearPassword()
}

data class SharedCardUi(val preview: SharedCardPreview, val action: SharedCardAction, val locked: Boolean)

sealed interface SharedImportUiState {
    data object Loading : SharedImportUiState

    data class PasswordNeeded(val wrongPassword: Boolean) : SharedImportUiState

    data class Choosing(val cards: List<SharedCardUi>) : SharedImportUiState

    data class Done(val result: BackupResult) : SharedImportUiState

    data object Closed : SharedImportUiState
}
