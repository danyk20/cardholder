package io.github.danyk20.cardholder.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.danyk20.cardholder.feature.settings.SettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object SettingsDestination

fun NavController.navigateToSettings() = navigate(SettingsDestination)

fun NavGraphBuilder.settingsScreen(onBack: () -> Unit) {
    composable<SettingsDestination> { SettingsRoute(onBack = onBack) }
}
