package io.github.danyk20.cardholder.feature.carddetail.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.ui.ProvideNavAnimatedVisibilityScope
import io.github.danyk20.cardholder.feature.carddetail.CardDetailRoute
import kotlinx.serialization.Serializable

@Serializable
data class CardDetailDestination(val cardId: String)

private const val DEEP_LINK_BASE = "cardholder://card"

/**
 * Opens a card's details, e.g. from an expiry reminder. Only used in explicit intents to this app;
 * there is no intent filter, so other apps can't open it.
 */
fun cardDetailDeepLink(cardId: CardId): String = "$DEEP_LINK_BASE/${cardId.value}"

fun NavController.navigateToCardDetail(cardId: CardId, navOptions: NavOptions? = null) =
    navigate(CardDetailDestination(cardId.value), navOptions)

fun NavGraphBuilder.cardDetailScreen(onBack: () -> Unit, onEdit: (CardId) -> Unit, onShowBarcode: (CardId) -> Unit) {
    composable<CardDetailDestination>(
        deepLinks = listOf(navDeepLink<CardDetailDestination>(basePath = DEEP_LINK_BASE)),
    ) {
        ProvideNavAnimatedVisibilityScope(this) {
            CardDetailRoute(onBack = onBack, onEdit = onEdit, onShowBarcode = onShowBarcode)
        }
    }
}
