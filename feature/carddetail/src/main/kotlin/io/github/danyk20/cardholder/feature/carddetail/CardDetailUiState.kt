package io.github.danyk20.cardholder.feature.carddetail

import io.github.danyk20.cardholder.core.model.CardDetails
import io.github.danyk20.cardholder.core.ui.CardSummary

sealed interface DetailsState {
    data object Loading : DetailsState

    data class Loaded(val details: CardDetails) : DetailsState

    data object Locked : DetailsState

    data object KeyInvalidated : DetailsState
}

/** What the user is authenticating for. */
enum class AuthAction {
    UNLOCK_DETAILS,
    REVEAL_CVV,
    REMOVE_LOCK,
}

data class CardDetailUiState(
    val summary: CardSummary? = null,
    val notFound: Boolean = false,
    val details: DetailsState = DetailsState.Loading,
    /** The revealed CVV; `null` while hidden. */
    val cvv: String? = null,
    val canProtect: Boolean = false,
    val pendingAuthentication: AuthAction? = null,
    val copiedLabel: String? = null,
    val isDeleted: Boolean = false,
    val showKeyInvalidatedError: Boolean = false,
) {
    override fun toString(): String = "CardDetailUiState(details=${details::class.simpleName}, cvv=██)"
}
