package io.github.danyk20.cardholder.feature.cardplaces.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.ui.ProvideNavAnimatedVisibilityScope
import io.github.danyk20.cardholder.feature.cardplaces.CardPlacesRoute
import kotlinx.serialization.Serializable

@Serializable
data class CardPlacesDestination(val cardId: String)

fun NavController.navigateToCardPlaces(cardId: CardId) = navigate(CardPlacesDestination(cardId.value))

fun NavGraphBuilder.cardPlacesScreen(onBack: () -> Unit) {
    composable<CardPlacesDestination> {
        ProvideNavAnimatedVisibilityScope(this) {
            CardPlacesRoute(onBack = onBack)
        }
    }
}
