package io.github.danyk20.cardholder.feature.cardeditor

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CardSide
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.scanning.BarcodeScanner
import io.github.danyk20.cardholder.core.scanning.nfc.NfcCardReadDialog
import io.github.danyk20.cardholder.core.scanning.nfc.isNfcAvailable
import io.github.danyk20.cardholder.core.scanning.rememberCardSideScanner
import io.github.danyk20.cardholder.core.ui.AuthenticationResult
import io.github.danyk20.cardholder.core.ui.R as UiR
import io.github.danyk20.cardholder.core.ui.SecureScreen
import io.github.danyk20.cardholder.core.ui.rememberAuthenticator
import io.github.danyk20.cardholder.core.ui.secureDialogProperties
import io.github.danyk20.cardholder.feature.cardeditor.ui.BankActions
import io.github.danyk20.cardholder.feature.cardeditor.ui.DetailsActions
import io.github.danyk20.cardholder.feature.cardeditor.ui.DetailsForm
import io.github.danyk20.cardholder.feature.cardeditor.ui.IdActions
import io.github.danyk20.cardholder.feature.cardeditor.ui.LogoActions
import io.github.danyk20.cardholder.feature.cardeditor.ui.LogoChoiceDialog
import io.github.danyk20.cardholder.feature.cardeditor.ui.LoyaltyActions
import io.github.danyk20.cardholder.feature.cardeditor.ui.SideActions
import io.github.danyk20.cardholder.feature.cardeditor.ui.SidesStep
import io.github.danyk20.cardholder.feature.cardeditor.ui.TypeStep

@Composable
fun CardEditorRoute(
    onClose: () -> Unit,
    onSaved: (CardId, isNew: Boolean) -> Unit,
    viewModel: CardEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val authenticator = rememberAuthenticator()
    val unlockTitle = stringResource(UiR.string.auth_title_unlock_card, state.title)

    SecureScreen()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    BackHandler { if (!viewModel.onBack()) onClose() }

    LaunchedEffect(state.pendingAuthentication) {
        val purpose = state.pendingAuthentication ?: return@LaunchedEffect
        authenticator.authenticate(unlockTitle) { result ->
            viewModel.onAuthenticationResult(purpose, result == AuthenticationResult.SUCCEEDED)
        }
    }
    LaunchedEffect(state.savedCardId) {
        state.savedCardId?.let { onSaved(it, !state.isEditing) }
    }

    var pendingSide by rememberSaveable { mutableStateOf(CardSide.FRONT) }
    var showBarcodeScanner by rememberSaveable { mutableStateOf(false) }
    var showNfcReader by rememberSaveable { mutableStateOf(false) }
    val nfcAvailable = remember(context) { isNfcAvailable(context) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onSideImagePicked(pendingSide, it.toString()) }
    }
    val pickPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onLogoPicked(it.toString()) } ?: viewModel.onLogoChoiceDismissed()
    }
    val logoActions = remember(viewModel) {
        LogoActions(
            onUseOfficial = viewModel::onUseOfficialLogo,
            onUpload = { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemove = viewModel::onRemoveLogo,
            onDismissChoice = viewModel::onLogoChoiceDismissed,
        )
    }
    val sideScanner = rememberCardSideScanner(
        onResult = { uri -> uri?.let { viewModel.onSideImagePicked(pendingSide, it) } },
        // Without Google Play services, fall back to choosing an existing photo.
        onUnavailable = pickPhoto,
    )
    val sideActions = SideActions(
        onScan = { side ->
            pendingSide = side
            sideScanner.scan()
        },
        onChoose = { side ->
            pendingSide = side
            pickPhoto()
        },
        onRemove = viewModel::onSideImageRemoved,
    )
    val detailsActions = remember(viewModel) {
        DetailsActions(
            onTitleChange = viewModel::onTitleChange,
            onNotesChange = viewModel::onNotesChange,
            onColorChange = viewModel::onColorChange,
            onLockedChange = viewModel::onLockedChange,
            onOpenSecuritySettings = {
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            },
            bank = BankActions(
                onBankSelected = viewModel::onBankSelected,
                onCustomBank = viewModel::onCustomBank,
                onReadWithNfc = if (nfcAvailable) ({ showNfcReader = true }) else null,
                onNumberChange = viewModel::onNumberChange,
                onExpiryChange = viewModel::onExpiryChange,
                onHolderChange = viewModel::onHolderChange,
                onCvvChange = viewModel::onCvvChange,
                onRemoveStoredCvv = viewModel::onRemoveStoredCvv,
            ),
            id = IdActions(
                onCountrySelected = viewModel::onCountrySelected,
                onDocumentNumberChange = viewModel::onDocumentNumberChange,
                onExpiryChange = viewModel::onIdExpiryChange,
            ),
            loyalty = LoyaltyActions(
                onShopSelected = viewModel::onShopSelected,
                onCustomShop = viewModel::onCustomShop,
                onCodeChange = viewModel::onCodeChange,
                onFormatChange = viewModel::onFormatChange,
                onScanBarcode = { showBarcodeScanner = true },
            ),
            logo = logoActions,
        )
    }

    if (showNfcReader) {
        NfcCardReadDialog(
            onCardRead = { data ->
                viewModel.onBankCardRead(data.number, data.expiry, data.holder)
                showNfcReader = false
            },
            onDismiss = { showNfcReader = false },
        )
    }
    state.logoChoiceFor?.let { choice -> LogoChoiceDialog(choice, logoActions) }
    val logoError = stringResource(R.string.editor_logo_download_failed)
    LaunchedEffect(state.logoDownloadFailed) {
        if (state.logoDownloadFailed) {
            Toast.makeText(context, logoError, Toast.LENGTH_LONG).show()
            viewModel.onLogoDownloadErrorShown()
        }
    }

    if (showBarcodeScanner) {
        BarcodeScanner(
            onScanned = {
                viewModel.onBarcodeScanned(it.code, it.format)
                showBarcodeScanner = false
            },
            onDismiss = { showBarcodeScanner = false },
        )
        return
    }

    CardEditorScreen(
        state = state,
        sideActions = sideActions,
        detailsActions = detailsActions,
        onBack = { if (!viewModel.onBack()) onClose() },
        onTypeSelected = viewModel::onTypeSelected,
        onSidesDone = viewModel::onSidesDone,
        onSave = viewModel::onSave,
        onUnlock = viewModel::onRetryAuthentication,
        onErrorShown = viewModel::onErrorShown,
        onKeepEditing = viewModel::onKeepEditing,
        onSaveDuplicate = { viewModel.onSave(duplicateConfirmed = true) },
        onDuplicateDismissed = viewModel::onDuplicateDismissed,
        onDiscard = {
            viewModel.onKeepEditing()
            onClose()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardEditorScreen(
    state: CardEditorUiState,
    sideActions: SideActions,
    detailsActions: DetailsActions,
    onBack: () -> Unit,
    onTypeSelected: (CardType) -> Unit,
    onSidesDone: () -> Unit,
    onSave: () -> Unit,
    onUnlock: () -> Unit,
    onErrorShown: () -> Unit,
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit,
    onSaveDuplicate: () -> Unit,
    onDuplicateDismissed: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isEditing) R.string.editor_title_edit else R.string.editor_title_add))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CardholderIcons.Back, contentDescription = stringResource(UiR.string.action_back))
                    }
                },
                actions = {
                    if (state.step == EditorStep.DETAILS && state.loadState == LoadState.READY) {
                        if (state.isSaving) {
                            CircularProgressIndicator(Modifier.padding(end = 16.dp).size(24.dp))
                        } else {
                            TextButton(onClick = onSave) { Text(stringResource(R.string.editor_save)) }
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (state.loadState) {
            LoadState.LOADING -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }

            LoadState.AUTHENTICATION_REQUIRED -> Message(
                text = stringResource(R.string.editor_locked_title),
                padding = padding,
                action = { Button(onClick = onUnlock) { Text(stringResource(UiR.string.action_unlock)) } },
            )

            LoadState.KEY_INVALIDATED -> Message(stringResource(UiR.string.auth_key_invalidated), padding)

            LoadState.NOT_FOUND -> Message(stringResource(R.string.editor_not_found), padding)

            LoadState.FAILED -> Message(stringResource(UiR.string.error_unexpected), padding)

            LoadState.READY -> when (state.step) {
                EditorStep.TYPE -> TypeStep(onTypeSelected = onTypeSelected, contentPadding = padding)

                EditorStep.SIDES -> SidesStep(
                    front = state.front,
                    back = state.back,
                    actions = sideActions,
                    onDone = onSidesDone,
                    contentPadding = padding,
                )

                EditorStep.DETAILS -> DetailsForm(state, sideActions, detailsActions, padding)
            }
        }
    }
    state.duplicate?.let { duplicate ->
        AlertDialog(
            onDismissRequest = onDuplicateDismissed,
            title = { Text(stringResource(R.string.editor_duplicate_title)) },
            text = {
                Text(
                    stringResource(
                        when (duplicate.reason) {
                            DuplicateReason.SAME_SHOP -> R.string.editor_duplicate_shop
                            DuplicateReason.SAME_BANK -> R.string.editor_duplicate_bank
                            DuplicateReason.SAME_NAME -> R.string.editor_duplicate_name
                        },
                        duplicate.existingTitle,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onSaveDuplicate) { Text(stringResource(R.string.editor_duplicate_save)) }
            },
            dismissButton = {
                TextButton(onClick = onDuplicateDismissed) { Text(stringResource(UiR.string.action_cancel)) }
            },
            properties = secureDialogProperties(),
        )
    }
    if (state.confirmDiscard) {
        AlertDialog(
            onDismissRequest = onKeepEditing,
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_message)) },
            confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.editor_discard)) } },
            dismissButton = {
                TextButton(onClick = onKeepEditing) { Text(stringResource(R.string.editor_keep_editing)) }
            },
            properties = secureDialogProperties(),
        )
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = onErrorShown,
            confirmButton = {
                TextButton(onClick = onErrorShown) { Text(stringResource(R.string.editor_ok)) }
            },
            text = {
                Text(
                    stringResource(
                        when (error) {
                            EditorError.KEY_INVALIDATED -> UiR.string.auth_key_invalidated
                            EditorError.UNEXPECTED -> UiR.string.error_unexpected
                        },
                    ),
                )
            },
            properties = secureDialogProperties(),
        )
    }
}

@Composable
private fun Message(text: String, padding: PaddingValues, action: (@Composable () -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
    ) {
        Icon(CardholderIcons.Lock, contentDescription = null, modifier = Modifier.size(48.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        action?.invoke()
    }
}
