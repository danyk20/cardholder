package io.github.danyk20.cardholder.feature.cardlist.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.feature.cardlist.CardListRoute
import kotlinx.serialization.Serializable

@Serializable
data object CardListDestination

fun NavGraphBuilder.cardListScreen(
    onCardClick: (Card) -> Unit,
    onCardLongClick: (Card) -> Unit,
    onAddCard: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    composable<CardListDestination> {
        CardListRoute(
            onCardClick = onCardClick,
            onCardLongClick = onCardLongClick,
            onAddCard = onAddCard,
            onOpenSettings = onOpenSettings,
        )
    }
}
