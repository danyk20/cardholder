package io.github.danyk20.cardholder.quickaccess

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.danyk20.cardholder.R
import io.github.danyk20.cardholder.core.model.Card
import javax.inject.Inject

/** App shortcuts (long-press the launcher icon) that open a loyalty card's code straight away. */
class CardShortcuts @Inject constructor(@ApplicationContext private val context: Context) {
    fun update(cards: List<Card>) {
        val limit = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtMost(MAX_SHORTCUTS)
        val shortcuts = quickAccessCards(cards, limit).map { card ->
            ShortcutInfoCompat.Builder(context, card.id.value)
                .setShortLabel(card.title.ifBlank { context.getString(R.string.app_name) })
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_card))
                .setIntent(showCodeIntent(context, card))
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    private companion object {
        /** Launchers show about four; more just get cut off. */
        const val MAX_SHORTCUTS = 4
    }
}
