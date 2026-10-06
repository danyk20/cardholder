package io.github.danyk20.cardholder

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.danyk20.cardholder.core.designsystem.theme.CardholderTheme
import io.github.danyk20.cardholder.core.domain.security.ScreenCapturePolicy
import io.github.danyk20.cardholder.core.model.ThemeMode
import io.github.danyk20.cardholder.core.ui.LocalScreenCaptureAllowed
import io.github.danyk20.cardholder.navigation.CardholderNavHost
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Single activity. A [FragmentActivity] because the biometric prompt requires one. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    @Inject
    lateinit var screenCapturePolicy: ScreenCapturePolicy

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value is MainActivityUiState.Loading }
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // While screenshots are allowed, card data must not end up in the recent apps thumbnail.
            lifecycleScope.launch {
                screenCapturePolicy.isAllowed.collect { allowed -> setRecentsScreenshotEnabled(!allowed) }
            }
        }
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val screenCaptureAllowed by screenCapturePolicy.isAllowed.collectAsStateWithLifecycle()
            val preferences = (uiState as? MainActivityUiState.Ready)?.preferences ?: return@setContent
            CardholderTheme(
                darkTheme = when (preferences.themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
                dynamicColor = preferences.useDynamicColor,
            ) {
                CompositionLocalProvider(LocalScreenCaptureAllowed provides screenCaptureAllowed) {
                    CardholderNavHost()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Without setRecentsScreenshotEnabled the thumbnail is taken while leaving, so revoke earlier.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) revokeScreenCapture()
    }

    override fun onStop() {
        super.onStop()
        revokeScreenCapture()
    }

    /** Screenshots are only allowed until the app leaves the screen; rotating doesn't count. */
    private fun revokeScreenCapture() {
        if (!isChangingConfigurations) screenCapturePolicy.setAllowed(false)
    }
}
