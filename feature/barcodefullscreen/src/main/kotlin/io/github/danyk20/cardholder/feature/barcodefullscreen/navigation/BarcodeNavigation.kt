package io.github.danyk20.cardholder.feature.barcodefullscreen.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.feature.barcodefullscreen.BarcodeRoute
import kotlinx.serialization.Serializable

@Serializable
data class BarcodeDestination(val cardId: String)

private const val DEEP_LINK_BASE = "cardholder://barcode"

/**
 * Opens a card's code full screen, e.g. from an app shortcut or the widget. Only used in explicit
 * intents to this app; there is no intent filter, so other apps can't open it.
 */
fun barcodeDeepLink(cardId: CardId): String = "$DEEP_LINK_BASE/${cardId.value}"

fun NavController.navigateToBarcode(cardId: CardId, navOptions: NavOptions? = null) =
    navigate(BarcodeDestination(cardId.value), navOptions)

fun NavGraphBuilder.barcodeScreen(onClose: () -> Unit, onOpenDetails: (CardId) -> Unit) {
    composable<BarcodeDestination>(deepLinks = listOf(navDeepLink<BarcodeDestination>(basePath = DEEP_LINK_BASE))) {
        BarcodeRoute(onClose = onClose, onOpenDetails = onOpenDetails)
    }
}
