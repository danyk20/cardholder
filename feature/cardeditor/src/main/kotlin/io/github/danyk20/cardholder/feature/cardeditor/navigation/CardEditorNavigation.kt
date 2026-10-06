package io.github.danyk20.cardholder.feature.cardeditor.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.feature.cardeditor.CardEditorRoute
import kotlinx.serialization.Serializable

/** Adds a new card when [cardId] is `null`, otherwise edits that card. */
@Serializable
data class CardEditorDestination(val cardId: String? = null)

fun NavController.navigateToCardEditor(cardId: CardId? = null, navOptions: NavOptions? = null) =
    navigate(CardEditorDestination(cardId?.value), navOptions)

fun NavGraphBuilder.cardEditorScreen(onClose: () -> Unit, onSaved: (CardId, isNew: Boolean) -> Unit) {
    composable<CardEditorDestination> {
        CardEditorRoute(onClose = onClose, onSaved = onSaved)
    }
}
