package io.github.danyk20.cardholder.places

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.danyk20.cardholder.core.domain.di.ApplicationScope
import io.github.danyk20.cardholder.core.domain.usecase.ArrivalSuggestionUseCase
import io.github.danyk20.cardholder.core.model.CardId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** "Not this place" on an arrival notification: forgets the place, so the card isn't suggested there again. */
class ArrivalActionReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface ReceiverEntryPoint {
        fun arrivals(): ArrivalSuggestionUseCase

        @ApplicationScope
        fun applicationScope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        val placeId = intent.getLongExtra(EXTRA_PLACE_ID, -1).takeIf { it >= 0 } ?: return
        val cardId = intent.getStringExtra(EXTRA_CARD_ID)?.let(::CardId) ?: return
        NotificationManagerCompat.from(context).cancel(ArrivalNotifier.tagOf(cardId), ArrivalNotifier.NOTIFICATION_ID)
        val entryPoint = EntryPointAccessors.fromApplication(context, ReceiverEntryPoint::class.java)
        val pending = goAsync()
        entryPoint.applicationScope().launch {
            try {
                entryPoint.arrivals().notThisPlace(placeId)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.w("Arrivals", "Couldn't forget the place", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_PLACE_ID = "place_id"
        private const val EXTRA_CARD_ID = "card_id"

        internal fun notThisPlace(context: Context, cardId: CardId, placeId: Long): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                placeId.hashCode(),
                Intent(context, ArrivalActionReceiver::class.java)
                    .putExtra(EXTRA_PLACE_ID, placeId)
                    .putExtra(EXTRA_CARD_ID, cardId.value),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
