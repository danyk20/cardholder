package io.github.danyk20.cardholder.core.ui

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Result of asking the user to authenticate. */
enum class AuthenticationResult {
    SUCCEEDED,

    /** The user cancelled or authentication failed too many times. */
    CANCELLED,

    /** No screen lock or biometric is set up on the device. */
    UNAVAILABLE,
}

/** Shows the system authentication prompt (strong biometric or device PIN/pattern/password). */
fun interface Authenticator {
    fun authenticate(title: String, onResult: (AuthenticationResult) -> Unit)
}

@Composable
fun rememberAuthenticator(): Authenticator {
    val context = LocalContext.current
    val subtitle = stringResource(R.string.auth_subtitle)
    return remember(context, subtitle) {
        val activity = context.findFragmentActivity()
        Authenticator { title, onResult ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                        onResult(AuthenticationResult.SUCCEEDED)

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(
                        when (errorCode) {
                            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                            BiometricPrompt.ERROR_NO_BIOMETRICS,
                            BiometricPrompt.ERROR_HW_NOT_PRESENT,
                            BiometricPrompt.ERROR_HW_UNAVAILABLE,
                            -> AuthenticationResult.UNAVAILABLE

                            else -> AuthenticationResult.CANCELLED
                        },
                    )
                },
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                    .setConfirmationRequired(false)
                    .build(),
            )
        }
    }
}

private fun android.content.Context.findFragmentActivity(): FragmentActivity {
    var current: android.content.Context = this
    while (current is android.content.ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    error("Authentication requires a FragmentActivity")
}
