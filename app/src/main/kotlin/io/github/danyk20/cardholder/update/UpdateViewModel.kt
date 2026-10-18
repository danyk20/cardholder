package io.github.danyk20.cardholder.update

import android.app.Activity
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.danyk20.cardholder.core.ui.launchSafely
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface UpdateStatus {
    data object Idle : UpdateStatus

    data object Checking : UpdateStatus

    data object UpToDate : UpdateStatus

    data object CheckFailed : UpdateStatus

    data class Available(val update: UpdateCheck.Available) : UpdateStatus

    /** [progress] is 0..1. */
    data class Downloading(val progress: Float) : UpdateStatus

    /** Android's installer or Google Play shows the rest. */
    data object Installing : UpdateStatus

    data class NeedsPermission(val update: UpdateCheck.Available) : UpdateStatus

    data object InstallFailed : UpdateStatus
}

data class UpdateUiState(
    val channel: UpdateChannel,
    val status: UpdateStatus = UpdateStatus.Idle,
    val autoCheck: Boolean = false,
    val lastCheck: Instant? = null,
)

/** "Check for updates" in Settings, and the weekly automatic check when the user switched it on. */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val source: UpdateSource,
    private val preferences: UpdatePreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        UpdateUiState(channel = source.channel, autoCheck = preferences.autoCheck, lastCheck = preferences.lastCheck),
    )
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    /** The update being installed, to offer it again if the user cancels Android's installer. */
    private var installing: UpdateCheck.Available? = null

    init {
        launchSafely {
            source.unfinishedInstalls.collect { outcome ->
                val update = installing ?: return@collect
                if (_uiState.value.status != UpdateStatus.Installing) return@collect
                setStatus(
                    when (outcome) {
                        InstallOutcome.CANCELLED -> UpdateStatus.Available(update)
                        InstallOutcome.FAILED -> UpdateStatus.InstallFailed
                    },
                )
            }
        }
    }

    /** Installing isn't busy: the user may cancel Android's or Google Play's installer and try again. */
    private val isBusy: Boolean
        get() = _uiState.value.status.let { it is UpdateStatus.Checking || it is UpdateStatus.Downloading }

    fun check() {
        if (isBusy) return
        setStatus(UpdateStatus.Checking)
        launchSafely(onError = { setStatus(UpdateStatus.CheckFailed) }) {
            val result = source.check()
            if (result != UpdateCheck.Failed) recordCheck()
            setStatus(
                when (result) {
                    UpdateCheck.UpToDate -> UpdateStatus.UpToDate
                    is UpdateCheck.Available -> UpdateStatus.Available(result)
                    UpdateCheck.Failed -> UpdateStatus.CheckFailed
                },
            )
        }
    }

    /** When the app opens: checks if the user switched that on and a week passed; only an update is shown. */
    fun checkIfDue() {
        if (isBusy || !preferences.isAutoCheckDue()) return
        launchSafely {
            val result = source.check()
            if (result != UpdateCheck.Failed) recordCheck()
            if (result is UpdateCheck.Available) setStatus(UpdateStatus.Available(result))
        }
    }

    fun install(activity: Activity) {
        val update = when (val status = _uiState.value.status) {
            is UpdateStatus.Available -> status.update
            is UpdateStatus.NeedsPermission -> status.update
            else -> return
        }
        installing = update
        setStatus(UpdateStatus.Downloading(0f))
        launchSafely(onError = { setStatus(UpdateStatus.InstallFailed) }) {
            val result = source.install(activity) { progress -> setStatus(UpdateStatus.Downloading(progress)) }
            setStatus(
                when (result) {
                    InstallResult.Started -> UpdateStatus.Installing
                    InstallResult.NeedsPermission -> UpdateStatus.NeedsPermission(update)
                    InstallResult.Failed -> UpdateStatus.InstallFailed
                },
            )
        }
    }

    fun onAutoCheckChange(enabled: Boolean) {
        preferences.autoCheck = enabled
        _uiState.update { it.copy(autoCheck = enabled) }
    }

    /** Closes a dialog ("Later", or after a message was read). */
    fun onDismiss() {
        if (!isBusy) setStatus(UpdateStatus.Idle)
    }

    private fun recordCheck() {
        preferences.recordCheck()
        _uiState.update { it.copy(lastCheck = preferences.lastCheck) }
    }

    private fun setStatus(status: UpdateStatus) = _uiState.update { it.copy(status = status) }
}
