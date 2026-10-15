package io.github.danyk20.cardholder

import android.content.ContentResolver
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.danyk20.cardholder.core.designsystem.theme.CardholderTheme
import io.github.danyk20.cardholder.core.domain.security.ScreenCapturePolicy
import io.github.danyk20.cardholder.core.model.ThemeMode
import io.github.danyk20.cardholder.core.ui.LocalScreenCaptureAllowed
import io.github.danyk20.cardholder.feature.settings.sharedimport.SharedImportDialog
import io.github.danyk20.cardholder.navigation.CardholderNavHost
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Single activity. A [FragmentActivity] because the biometric prompt requires one. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    @Inject
    lateinit var screenCapturePolicy: ScreenCapturePolicy

    /** A shared or backup file the app was opened with, until the user imported or dismissed it. */
    private var sharedFileUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // After a configuration change the intent is delivered again, but was already handled.
        sharedFileUri =
            if (savedInstanceState == null) intent.sharedFileUri() else savedInstanceState.getString(KEY_SHARED_FILE)
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
            val preferences = when (val state = uiState) {
                MainActivityUiState.Loading -> return@setContent
                is MainActivityUiState.Ready -> state.preferences
                is MainActivityUiState.StorageUnavailable -> state.preferences
            }
            val darkTheme = when (preferences.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // System bar icons follow the app's theme, which can differ from the system's.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                )
                onDispose {}
            }
            CardholderTheme(darkTheme = darkTheme, dynamicColor = preferences.useDynamicColor) {
                // Screens fade over the app's background, not the window's, so dark mode doesn't flash light.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (uiState is MainActivityUiState.StorageUnavailable) {
                        StorageErrorScreen(onEraseAllData = viewModel::onEraseAllData)
                    } else {
                        CompositionLocalProvider(LocalScreenCaptureAllowed provides screenCaptureAllowed) {
                            CardholderNavHost()
                            sharedFileUri?.let { uri -> SharedImportDialog(uri, onClose = { sharedFileUri = null }) }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.sharedFileUri()?.let { sharedFileUri = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SHARED_FILE, sharedFileUri)
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

    private companion object {
        const val KEY_SHARED_FILE = "shared_file"
    }
}

/** The file of a "open with" or "share to" intent, if this intent carries one. */
private fun Intent.sharedFileUri(): String? {
    val uri = when (action) {
        Intent.ACTION_VIEW -> data
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
        else -> null
    }
    return uri?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT || it.scheme == ContentResolver.SCHEME_FILE }
        ?.toString()
}
