package io.github.danyk20.cardholder.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.feature.barcodefullscreen.navigation.barcodeScreen
import io.github.danyk20.cardholder.feature.barcodefullscreen.navigation.navigateToBarcode
import io.github.danyk20.cardholder.feature.carddetail.navigation.CardDetailDestination
import io.github.danyk20.cardholder.feature.carddetail.navigation.cardDetailScreen
import io.github.danyk20.cardholder.feature.carddetail.navigation.navigateToCardDetail
import io.github.danyk20.cardholder.feature.cardeditor.navigation.CardEditorDestination
import io.github.danyk20.cardholder.feature.cardeditor.navigation.cardEditorScreen
import io.github.danyk20.cardholder.feature.cardeditor.navigation.navigateToCardEditor
import io.github.danyk20.cardholder.feature.cardlist.navigation.CardListDestination
import io.github.danyk20.cardholder.feature.cardlist.navigation.cardListScreen
import io.github.danyk20.cardholder.feature.settings.navigation.navigateToSettings
import io.github.danyk20.cardholder.feature.settings.navigation.settingsScreen

@Composable
fun CardholderNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = CardListDestination, modifier = modifier) {
        cardListScreen(
            // Loyalty cards are used at the till, so a tap goes straight to the scannable code.
            onCardClick = { card ->
                if (card.type == CardType.LOYALTY) {
                    navController.navigateToBarcode(card.id)
                } else {
                    navController.navigateToCardDetail(card.id)
                }
            },
            onCardLongClick = { navController.navigateToCardDetail(it.id) },
            onAddCard = { navController.navigateToCardEditor() },
            onOpenSettings = navController::navigateToSettings,
        )
        cardEditorScreen(
            onClose = navController::popBackStack,
            onSaved = { id, isNew ->
                if (isNew) {
                    navController.navigateToCardDetail(
                        id,
                        navOptions { popUpTo<CardEditorDestination> { inclusive = true } },
                    )
                } else {
                    navController.popBackStack()
                }
            },
        )
        cardDetailScreen(
            onBack = navController::popBackStack,
            onEdit = { navController.navigateToCardEditor(it) },
            onShowBarcode = { navController.navigateToBarcode(it) },
        )
        settingsScreen(onBack = navController::popBackStack)
        barcodeScreen(
            onClose = navController::popBackStack,
            onOpenDetails = { id ->
                val returnedToDetail = navController.previousBackStackEntry?.destination
                    ?.hasRoute(CardDetailDestination::class) == true && navController.popBackStack()
                if (!returnedToDetail) navController.navigateToCardDetail(id)
            },
        )
    }
}
