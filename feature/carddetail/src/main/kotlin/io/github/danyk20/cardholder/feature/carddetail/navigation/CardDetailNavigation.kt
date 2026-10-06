package io.github.danyk20.cardholder.feature.carddetail.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.feature.carddetail.CardDetailRoute
import kotlinx.serialization.Serializable

@Serializable
data class CardDetailDestination(val cardId: String)

fun NavController.navigateToCardDetail(cardId: CardId, navOptions: NavOptions? = null) =
    navigate(CardDetailDestination(cardId.value), navOptions)

fun NavGraphBuilder.cardDetailScreen(onBack: () -> Unit, onEdit: (CardId) -> Unit, onShowBarcode: (CardId) -> Unit) {
    composable<CardDetailDestination> {
        CardDetailRoute(onBack = onBack, onEdit = onEdit, onShowBarcode = onShowBarcode)
    }
}
