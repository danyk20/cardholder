package io.github.danyk20.cardholder.feature.barcodefullscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.danyk20.cardholder.core.barcode.BarcodeEncoder
import io.github.danyk20.cardholder.core.barcode.BarcodeImage
import io.github.danyk20.cardholder.core.designsystem.icon.CardholderIcons
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.ui.AuthenticationResult
import io.github.danyk20.cardholder.core.ui.LockedContent
import io.github.danyk20.cardholder.core.ui.MaxBrightness
import io.github.danyk20.cardholder.core.ui.R as UiR
import io.github.danyk20.cardholder.core.ui.SecureScreen
import io.github.danyk20.cardholder.core.ui.barcodeFormatLabel
import io.github.danyk20.cardholder.core.ui.rememberAuthenticator

@Composable
fun BarcodeRoute(onClose: () -> Unit, onOpenDetails: (CardId) -> Unit, viewModel: BarcodeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val authenticator = rememberAuthenticator()
    val authTitle = stringResource(UiR.string.auth_title_unlock_card, (state as? BarcodeUiState.Ready)?.title.orEmpty())
    val requestAuthentication = {
        authenticator.authenticate(authTitle) { viewModel.onAuthenticationResult(it == AuthenticationResult.SUCCEEDED) }
    }
    LaunchedEffect(state) {
        if ((state as? BarcodeUiState.AuthenticationRequired)?.fromLock == true) requestAuthentication()
        // Deleted from the details screen opened on top of this one: there is nothing left to show.
        if (state == BarcodeUiState.Removed) onClose()
    }
    BarcodeScreen(
        state = state,
        onClose = onClose,
        onOpenDetails = { onOpenDetails(viewModel.cardId) },
        onUnlock = requestAuthentication,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BarcodeScreen(
    state: BarcodeUiState,
    onClose: () -> Unit,
    onOpenDetails: () -> Unit,
    onUnlock: () -> Unit,
) {
    SecureScreen()
    // Scanners need a bright, high-contrast code regardless of the app theme.
    Scaffold(
        containerColor = Color.White,
        contentColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text((state as? BarcodeUiState.Ready)?.title.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(CardholderIcons.Close, contentDescription = stringResource(R.string.barcode_close))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenDetails) {
                        Icon(CardholderIcons.More, contentDescription = stringResource(R.string.barcode_details))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black,
                    actionIconContentColor = Color.Black,
                ),
            )
        },
    ) { padding ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(padding),
        ) {
            when (state) {
                BarcodeUiState.Loading -> CircularProgressIndicator()

                is BarcodeUiState.Ready -> ReadyContent(state)

                is BarcodeUiState.AuthenticationRequired ->
                    LockedContent(stringResource(UiR.string.card_locked_message), onUnlock)

                BarcodeUiState.KeyInvalidated -> LockedContent(stringResource(UiR.string.auth_key_invalidated), null)

                BarcodeUiState.NotFound -> Text(stringResource(UiR.string.card_not_found))

                BarcodeUiState.Failed -> Text(stringResource(UiR.string.error_unexpected))

                // Closing; never show the removed card's code.
                BarcodeUiState.Removed -> Unit
            }
        }
    }
}

@Composable
private fun ReadyContent(state: BarcodeUiState.Ready) {
    MaxBrightness()
    val formatLabel = barcodeFormatLabel(state.format)
    val encodable = remember(state.code, state.format) { BarcodeEncoder.canEncode(state.code, state.format) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier
            .widthIn(max = 560.dp)
            .padding(horizontal = 16.dp),
    ) {
        if (encodable) {
            BarcodeImage(
                content = state.code,
                format = state.format,
                contentDescription = stringResource(R.string.barcode_description, formatLabel, state.shopName),
                modifier = Modifier.fillMaxWidth(if (state.format.isSquare) SQUARE_WIDTH_FRACTION else 1f),
            )
        } else {
            Text(
                stringResource(R.string.barcode_unencodable, formatLabel),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = state.code,
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            color = Color.Black,
        )
        Text(
            text = stringResource(R.string.barcode_hint),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = Color.DarkGray,
        )
    }
}

private const val SQUARE_WIDTH_FRACTION = 0.8f
