package io.github.danyk20.cardholder.quickaccess

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.danyk20.cardholder.core.model.CardId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The cards the widget's last "sort by location" found nearby, kept until the next refresh. Only card
 * ids are stored (no card data or places), and Android backup is disabled for the app.
 */
@Singleton
class WidgetNearbyOrder @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun cardIds(): List<CardId> =
        prefs.getString(KEY_CARD_IDS, null)?.split(SEPARATOR)?.filter(String::isNotBlank)?.map(::CardId).orEmpty()

    /** Replaces the order; an empty list brings back the usual one. */
    fun set(ids: List<CardId>) = prefs.edit { putString(KEY_CARD_IDS, ids.joinToString(SEPARATOR) { it.value }) }

    private companion object {
        const val PREFS = "widget"
        const val KEY_CARD_IDS = "nearby_card_ids"
        const val SEPARATOR = "\n"
    }
}
