package io.github.danyk20.cardholder.quickaccess

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import io.github.danyk20.cardholder.MainActivity
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.feature.barcodefullscreen.navigation.barcodeDeepLink

/**
 * The loyalty cards offered outside the app (shortcuts, widget): the cards found [nearby] first, in that
 * order, then favourites, the most used, and by name. Locked cards are left out, since the user chose to
 * keep them private.
 */
internal fun quickAccessCards(cards: List<Card>, limit: Int, nearby: List<CardId> = emptyList()): List<Card> {
    val nearbyRank = nearby.withIndex().associate { (index, id) -> id to index }
    return cards
        .filter { it.type == CardType.LOYALTY && !it.isLocked }
        .sortedWith(
            compareBy<Card> { nearbyRank[it.id] ?: Int.MAX_VALUE }
                .thenByDescending { it.isFavourite }
                .thenByDescending { it.useCount }
                .thenByDescending { it.lastUsedAt }
                .thenBy { it.title },
        )
        .take(limit)
}

/** Opens [card]'s code full screen; explicit, so only this app can handle it. */
internal fun showCodeIntent(context: Context, card: Card): Intent = Intent(context, MainActivity::class.java)
    .setAction(Intent.ACTION_VIEW)
    .setData(barcodeDeepLink(card.id).toUri())
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
