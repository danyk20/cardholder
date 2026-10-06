package io.github.danyk20.cardholder.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import io.github.danyk20.cardholder.feature.cardeditor.navigation.cardEditorScreen
import io.github.danyk20.cardholder.feature.cardeditor.navigation.navigateToCardEditor
import io.github.danyk20.cardholder.feature.cardlist.navigation.CardListDestination
import io.github.danyk20.cardholder.feature.cardlist.navigation.cardListScreen

@Composable
fun CardholderNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = CardListDestination, modifier = modifier) {
        cardListScreen(
            onCardClick = { navController.navigateToCardEditor(it.id) },
            onCardLongClick = { navController.navigateToCardEditor(it.id) },
            onAddCard = { navController.navigateToCardEditor() },
            onOpenSettings = {},
        )
        cardEditorScreen(
            onClose = navController::popBackStack,
            onSaved = { _, _ -> navController.popBackStack() },
        )
    }
}
