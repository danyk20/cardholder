package io.github.danyk20.cardholder.feature.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.domain.location.LocationPermission
import io.github.danyk20.cardholder.core.model.PlaceSuggestions
import io.github.danyk20.cardholder.core.ui.findActivity
import io.github.danyk20.cardholder.core.ui.openAppSettings
import io.github.danyk20.cardholder.core.ui.openLocationSettings

/** What the settings show about suggestions by location. */
data class LocationSettingsUiState(
    val permission: LocationPermission = LocationPermission.NONE,
    val isLocationEnabled: Boolean = true,
    val isArrivalSupported: Boolean = false,
    val placeCount: Int = 0,
    /** A message about the last mode change, until it was shown. */
    val message: LocationMessage? = null,
)

enum class LocationMessage {
    PRECISE_NEEDED,
    NOTIFICATIONS_NEEDED,
    ALWAYS_NEEDED,
    PLACES_FORGOTTEN,
}

/** Why suggestions are on but can't work right now; `null` while they work. */
internal enum class PausedReason { NO_PERMISSION, LOCATION_OFF, NO_BACKGROUND }

internal fun pausedReason(mode: PlaceSuggestions, location: LocationSettingsUiState): PausedReason? = when {
    mode == PlaceSuggestions.OFF -> null

    !location.permission.isPrecise -> PausedReason.NO_PERMISSION

    !location.isLocationEnabled -> PausedReason.LOCATION_OFF

    mode == PlaceSuggestions.ARRIVAL && location.permission != LocationPermission.PRECISE_ALWAYS ->
        PausedReason.NO_BACKGROUND

    else -> null
}

/** What the user can do in the "Card suggestions" section. */
class LocationSettingsActions(
    /** The user picked a mode; permissions are asked for before it's applied. */
    val onModeSelect: (PlaceSuggestions) -> Unit = {},
    val onForgetAll: () -> Unit = {},
)

@Composable
internal fun LocationSuggestionsSection(
    mode: PlaceSuggestions,
    location: LocationSettingsUiState,
    actions: LocationSettingsActions,
) {
    val context = LocalContext.current
    Column(Modifier.selectableGroup()) {
        PlaceSuggestions.entries.forEach { option ->
            val enabled = option != PlaceSuggestions.ARRIVAL || location.isArrivalSupported
            ListItem(
                headlineContent = { Text(stringResource(option.title)) },
                supportingContent = {
                    Text(stringResource(if (enabled) option.description else R.string.settings_suggestions_needs_play))
                },
                leadingContent = { RadioButton(selected = option == mode, onClick = null, enabled = enabled) },
                modifier = Modifier.selectable(
                    selected = option == mode,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = { if (option != mode) actions.onModeSelect(option) },
                ),
            )
        }
    }
    pausedReason(mode, location)?.let { reason ->
        ListItem(
            leadingContent = { Icon(CardholderIcons.PlaceOff, contentDescription = null) },
            headlineContent = {
                Text(stringResource(R.string.settings_suggestions_paused), color = MaterialTheme.colorScheme.error)
            },
            supportingContent = { Text(stringResource(reason.description)) },
            modifier = Modifier.clickable(role = Role.Button) {
                if (reason == PausedReason.LOCATION_OFF) context.openLocationSettings() else context.openAppSettings()
            },
        )
    }
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_forget_places)) },
        supportingContent = { Text(stringResource(R.string.settings_learned_places, location.placeCount)) },
        modifier = Modifier.clickable(enabled = location.placeCount > 0, role = Role.Button) { confirmForget = true },
    )
    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text(stringResource(R.string.settings_forget_places_title)) },
            text = { Text(stringResource(R.string.settings_forget_places_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    actions.onForgetAll()
                }) { Text(stringResource(R.string.settings_forget)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }
}

private val PlaceSuggestions.title: Int
    get() = when (this) {
        PlaceSuggestions.OFF -> R.string.settings_suggestions_off
        PlaceSuggestions.IN_APP -> R.string.settings_suggestions_in_app
        PlaceSuggestions.ARRIVAL -> R.string.settings_suggestions_arrival
    }

private val PlaceSuggestions.description: Int
    get() = when (this) {
        PlaceSuggestions.OFF -> R.string.settings_suggestions_off_description
        PlaceSuggestions.IN_APP -> R.string.settings_suggestions_in_app_description
        PlaceSuggestions.ARRIVAL -> R.string.settings_suggestions_arrival_description
    }

private val PausedReason.description: Int
    get() = when (this) {
        PausedReason.NO_PERMISSION -> R.string.settings_suggestions_paused_permission
        PausedReason.LOCATION_OFF -> R.string.settings_suggestions_paused_location
        PausedReason.NO_BACKGROUND -> R.string.settings_suggestions_paused_background
    }

/** A step of switching suggestions on that needs the user. */
private enum class FlowStep {
    /** Explains why precise location is needed before Android asks. */
    FOREGROUND_RATIONALE,

    /** Android won't ask again; the permission has to be granted in the app's settings. */
    FOREGROUND_DENIED,
    NOTIFICATIONS,

    /** Google Play's required disclosure before asking for location in the background. */
    BACKGROUND_DISCLOSURE,

    /** Turning suggestions off: keep or delete the learned places. */
    KEEP_OR_DELETE,
}

/**
 * Applies the [requested] mode once the permissions it needs are granted: precise location for any
 * mode, plus notifications and location all the time for arrival notifications. Falls back to
 * suggestions in the app when arrival notifications aren't allowed.
 */
@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun PlaceSuggestionsFlow(
    requested: PlaceSuggestions?,
    placeCount: Int,
    onApply: (PlaceSuggestions) -> Unit,
    onForgetAll: () -> Unit,
    onMessage: (LocationMessage) -> Unit,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf<FlowStep?>(null) }
    var notificationsAsked by rememberSaveable { mutableStateOf(false) }

    fun finish(mode: PlaceSuggestions?, message: LocationMessage? = null) {
        step = null
        notificationsAsked = false
        mode?.let(onApply)
        message?.let(onMessage)
        onFinished()
    }

    fun advance() {
        val target = requested ?: return
        step = when {
            target == PlaceSuggestions.OFF -> if (placeCount > 0) FlowStep.KEEP_OR_DELETE else null

            !context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION) -> FlowStep.FOREGROUND_RATIONALE

            target == PlaceSuggestions.IN_APP -> null

            context.needsNotificationPermission() && !notificationsAsked -> FlowStep.NOTIFICATIONS

            context.needsNotificationPermission() -> return finish(
                PlaceSuggestions.IN_APP,
                LocationMessage.NOTIFICATIONS_NEEDED,
            )

            !context.isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) -> FlowStep.BACKGROUND_DISCLOSURE

            else -> null
        }
        if (step == null) finish(target)
    }

    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        when {
            context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION) -> advance()

            context.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION) ->
                finish(null, LocationMessage.PRECISE_NEEDED)

            context.findActivity()?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION)
            } == true -> step = FlowStep.FOREGROUND_DENIED

            else -> finish(null)
        }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAsked = true
        advance()
    }
    // On Android 11+ this opens the app's location settings, where "Allow all the time" is chosen.
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            finish(PlaceSuggestions.ARRIVAL)
        } else {
            finish(PlaceSuggestions.IN_APP, LocationMessage.ALWAYS_NEEDED)
        }
    }

    LaunchedEffect(requested) {
        if (requested != null && step == null) advance()
    }
    LaunchedEffect(step) {
        if (step == FlowStep.NOTIFICATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    when (step) {
        FlowStep.FOREGROUND_RATIONALE -> FlowDialog(
            title = R.string.settings_location_rationale_title,
            text = stringResource(R.string.settings_location_rationale),
            confirm = R.string.settings_continue,
            onConfirm = {
                foreground.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                )
            },
            onDismiss = { finish(null) },
        )

        FlowStep.FOREGROUND_DENIED -> FlowDialog(
            title = R.string.settings_location_rationale_title,
            text = stringResource(R.string.settings_location_denied),
            confirm = R.string.settings_open_settings,
            onConfirm = {
                context.openAppSettings()
                finish(null)
            },
            onDismiss = { finish(null) },
        )

        FlowStep.BACKGROUND_DISCLOSURE -> FlowDialog(
            title = R.string.settings_background_title,
            text = stringResource(R.string.settings_background_disclosure, context.allowAllTheTimeLabel()),
            confirm = R.string.settings_continue,
            onConfirm = { background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
            onDismiss = { finish(PlaceSuggestions.IN_APP, LocationMessage.ALWAYS_NEEDED) },
        )

        FlowStep.KEEP_OR_DELETE -> AlertDialog(
            onDismissRequest = { finish(null) },
            title = { Text(stringResource(R.string.settings_suggestions_off_title)) },
            text = { Text(stringResource(R.string.settings_suggestions_off_message)) },
            confirmButton = {
                TextButton(onClick = { finish(PlaceSuggestions.OFF) }) { Text(stringResource(R.string.settings_keep)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    onForgetAll()
                    finish(PlaceSuggestions.OFF)
                }) { Text(stringResource(R.string.settings_delete_places)) }
            },
        )

        FlowStep.NOTIFICATIONS, null -> Unit
    }
}

@Composable
private fun FlowDialog(title: Int, text: String, confirm: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_not_now)) } },
    )
}

private fun Context.isGranted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.needsNotificationPermission() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
    !isGranted(Manifest.permission.POST_NOTIFICATIONS)

/** The system's own, translated name of the "Allow all the time" option. */
private fun Context.allowAllTheTimeLabel(): CharSequence = packageManager.backgroundPermissionOptionLabel
