package io.github.danyk20.cardholder.feature.cardlist

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.ui.AuthenticationResult
import io.github.danyk20.cardholder.core.ui.rememberAuthenticator

/** The dialogs of sharing, in order; bank cards need an explicit confirmation first. */
internal enum class ShareStep { BANK_WARNING, PASSWORD }

/** The first step of sharing [selected]. */
internal fun firstShareStep(selected: List<Card>): ShareStep =
    if (selected.any { it.type == CardType.BANK }) ShareStep.BANK_WARNING else ShareStep.PASSWORD

/**
 * Shows the dialog of the current share [step], asks for authentication when the [selected] cards
 * include CVVs or locked cards, and opens the share sheet (or reports a failure) once [share] is done.
 */
@Composable
internal fun ShareFlow(
    selected: List<Card>,
    step: ShareStep?,
    onStepChange: (ShareStep?) -> Unit,
    share: ShareState,
    snackbarHostState: SnackbarHostState,
    onShare: (password: CharArray?) -> Unit,
    onShareHandled: () -> Unit,
) {
    val context = LocalContext.current
    val authenticator = rememberAuthenticator()
    val authTitle = stringResource(R.string.cardlist_share_auth_title)
    val failedMessage = stringResource(R.string.cardlist_share_failed)
    val authFailedMessage = stringResource(R.string.cardlist_share_auth_failed)
    LaunchedEffect(share) {
        when (share) {
            is ShareState.Ready -> {
                onShareHandled()
                context.launchShareSheet(share.uri, share.cardCount)
            }

            ShareState.Failed, ShareState.AuthenticationFailed -> {
                onShareHandled()
                snackbarHostState.showSnackbar(
                    if (share == ShareState.Failed) failedMessage else authFailedMessage,
                )
            }

            ShareState.Idle, ShareState.InProgress -> Unit
        }
    }

    when (step) {
        ShareStep.BANK_WARNING -> BankCardWarningDialog(
            bankCards = selected.filter { it.type == CardType.BANK },
            onConfirm = { onStepChange(ShareStep.PASSWORD) },
            onDismiss = { onStepChange(null) },
        )

        ShareStep.PASSWORD -> SharePasswordDialog(
            onDismiss = { onStepChange(null) },
            onConfirm = { password ->
                onStepChange(null)
                // CVVs and locked cards are only ever revealed after an explicit prompt.
                if (selected.any { it.isLocked || it.hasCvv }) {
                    authenticator.authenticate(authTitle) { result ->
                        if (result == AuthenticationResult.SUCCEEDED) {
                            onShare(password)
                        } else {
                            password?.fill(' ')
                        }
                    }
                } else {
                    onShare(password)
                }
            },
        )

        null -> Unit
    }
}
