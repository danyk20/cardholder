package io.github.danyk20.cardholder.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import io.github.danyk20.cardholder.feature.settings.SettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object SettingsDestination

/**
 * Opens Settings, e.g. from the widget while card suggestions are off. Only used in explicit intents to
 * this app; there is no intent filter, so other apps can't open it.
 */
const val SETTINGS_DEEP_LINK = "cardholder://settings"

fun NavController.navigateToSettings() = navigate(SettingsDestination)

fun NavGraphBuilder.settingsScreen(onBack: () -> Unit) {
    composable<SettingsDestination>(
        deepLinks = listOf(navDeepLink<SettingsDestination>(basePath = SETTINGS_DEEP_LINK)),
    ) { SettingsRoute(onBack = onBack) }
}
