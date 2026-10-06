package io.github.danyk20.cardholder.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.domain.model.BackupResult
import io.github.danyk20.cardholder.core.model.ThemeMode
import io.github.danyk20.cardholder.core.ui.AuthenticationResult
import io.github.danyk20.cardholder.core.ui.R as UiR
import io.github.danyk20.cardholder.core.ui.rememberAuthenticator
import java.time.LocalDate

@Composable
fun SettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val authenticator = rememberAuthenticator()
    val exportTitle = stringResource(UiR.string.auth_title_export)

    var passwordPurpose by remember { mutableStateOf<PasswordPurpose?>(null) }
    // Kept only in memory between the dialog, the file picker and the authentication prompt.
    var pendingPassword by remember { mutableStateOf<CharArray?>(null) }
    var pendingImportUri by remember { mutableStateOf<String?>(null) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
    ) { uri ->
        val password = pendingPassword
        if (uri == null || password == null) {
            pendingPassword?.fill(' ')
            pendingPassword = null
            return@rememberLauncherForActivityResult
        }
        authenticator.authenticate(exportTitle) { result ->
            if (result == AuthenticationResult.SUCCEEDED) viewModel.export(uri.toString(), password.copyOf())
            password.fill(' ')
            pendingPassword = null
        }
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingImportUri = uri.toString()
            passwordPurpose = PasswordPurpose.IMPORT
        }
    }

    SettingsScreen(
        state = state,
        onBack = onBack,
        onThemeModeChange = viewModel::onThemeModeChange,
        onDynamicColorChange = viewModel::onDynamicColorChange,
        onExport = { passwordPurpose = PasswordPurpose.EXPORT },
        onImport = { openDocument.launch(arrayOf("*/*")) },
        onResultShown = viewModel::onResultShown,
    )

    passwordPurpose?.let { purpose ->
        PasswordDialog(
            purpose = purpose,
            onDismiss = { passwordPurpose = null },
            onConfirm = { password, replaceExisting ->
                passwordPurpose = null
                when (purpose) {
                    PasswordPurpose.EXPORT -> {
                        pendingPassword = password
                        createDocument.launch("cardholder-${LocalDate.now()}.$BACKUP_EXTENSION")
                    }

                    PasswordPurpose.IMPORT -> pendingImportUri?.let { uri ->
                        viewModel.import(uri, password, replaceExisting)
                        pendingImportUri = null
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onResultShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resultMessage = state.backup.result?.let { resultMessage(it) }
    LaunchedEffect(resultMessage) {
        resultMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(resultMessage)
        onResultShown()
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CardholderIcons.Back, contentDescription = stringResource(UiR.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
        ) {
            SectionHeader(stringResource(R.string.settings_appearance))
            ThemeOptions(selected = state.preferences.themeMode, onSelect = onThemeModeChange)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                supportingContent = { Text(stringResource(R.string.settings_dynamic_color_description)) },
                trailingContent = { Switch(checked = state.preferences.useDynamicColor, onCheckedChange = null) },
                modifier = Modifier.toggleable(
                    value = state.preferences.useDynamicColor,
                    role = Role.Switch,
                    onValueChange = onDynamicColorChange,
                ),
            )
            HorizontalDivider()

            SectionHeader(stringResource(R.string.settings_backup))
            val running = state.backup.inProgress
            BackupItem(
                title = R.string.settings_export,
                description = R.string.settings_export_description,
                isRunning = running == BackupOperation.EXPORT,
                enabled = running == null,
                onClick = onExport,
            )
            BackupItem(
                title = R.string.settings_import,
                description = R.string.settings_import_description,
                isRunning = running == BackupOperation.IMPORT,
                enabled = running == null,
                onClick = onImport,
            )
            HorizontalDivider()

            SectionHeader(stringResource(R.string.settings_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version, appVersion())) },
                supportingContent = { Text(stringResource(R.string.settings_privacy)) },
            )
            val uriHandler = LocalUriHandler.current
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_privacy_policy)) },
                modifier = Modifier.selectable(selected = false, role = Role.Button) {
                    uriHandler.openUri(PRIVACY_POLICY_URL)
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_logo_credits)) },
                supportingContent = { Text(stringResource(R.string.settings_logo_credits_description)) },
                modifier = Modifier.selectable(selected = false, role = Role.Button) {
                    uriHandler.openUri(LOGO_CREDITS_URL)
                },
            )
        }
    }
}

@Composable
private fun ThemeOptions(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Column(Modifier.selectableGroup()) {
        ThemeMode.entries.forEach { mode ->
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(
                            when (mode) {
                                ThemeMode.SYSTEM -> R.string.settings_theme_system
                                ThemeMode.LIGHT -> R.string.settings_theme_light
                                ThemeMode.DARK -> R.string.settings_theme_dark
                            },
                        ),
                    )
                },
                leadingContent = { RadioButton(selected = mode == selected, onClick = null) },
                modifier = Modifier.selectable(
                    selected = mode == selected,
                    role = Role.RadioButton,
                    onClick = { onSelect(mode) },
                ),
            )
        }
    }
}

@Composable
private fun BackupItem(title: Int, description: Int, isRunning: Boolean, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = {
            Text(stringResource(if (isRunning) R.string.settings_backup_working else description))
        },
        trailingContent = if (isRunning) ({ CircularProgressIndicator(Modifier.padding(4.dp)) }) else null,
        modifier = Modifier.selectable(selected = false, enabled = enabled, role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun resultMessage(result: BackupResult): String = when (result) {
    is BackupResult.Exported ->
        pluralStringResource(R.plurals.settings_result_exported, result.cardCount, result.cardCount)

    is BackupResult.Imported -> buildList {
        add(pluralStringResource(R.plurals.settings_result_imported, result.imported, result.imported))
        if (result.skipped > 0) {
            add(pluralStringResource(R.plurals.settings_result_skipped, result.skipped, result.skipped))
        }
    }.joinToString(". ")

    BackupResult.WrongPassword -> stringResource(R.string.settings_result_wrong_password)

    BackupResult.InvalidFile -> stringResource(R.string.settings_result_invalid_file)

    BackupResult.AuthenticationRequired -> stringResource(R.string.settings_result_auth_required)

    BackupResult.KeyInvalidated -> stringResource(UiR.string.auth_key_invalidated)

    BackupResult.DeviceNotSecure -> stringResource(R.string.settings_result_device_not_secure)

    is BackupResult.Failed -> stringResource(R.string.settings_result_failed)
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
}

private const val PRIVACY_POLICY_URL = "https://danyk20.github.io/cardholder/privacy/"
private const val LOGO_CREDITS_URL = "https://github.com/danyk20/cardholder/blob/main/docs/LOGOS.md"
private const val BACKUP_MIME_TYPE = "application/octet-stream"
private const val BACKUP_EXTENSION = "cardholder"
