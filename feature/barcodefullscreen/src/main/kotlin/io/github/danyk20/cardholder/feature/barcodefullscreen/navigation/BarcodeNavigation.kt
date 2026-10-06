package io.github.danyk20.cardholder.feature.barcodefullscreen.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.feature.barcodefullscreen.BarcodeRoute
import kotlinx.serialization.Serializable

@Serializable
data class BarcodeDestination(val cardId: String)

fun NavController.navigateToBarcode(cardId: CardId, navOptions: NavOptions? = null) =
    navigate(BarcodeDestination(cardId.value), navOptions)

fun NavGraphBuilder.barcodeScreen(onClose: () -> Unit, onOpenDetails: (CardId) -> Unit) {
    composable<BarcodeDestination> {
        BarcodeRoute(onClose = onClose, onOpenDetails = onOpenDetails)
    }
}
