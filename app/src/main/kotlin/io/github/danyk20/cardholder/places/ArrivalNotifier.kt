package io.github.danyk20.cardholder.places

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.danyk20.cardholder.MainActivity
import io.github.danyk20.cardholder.R
import io.github.danyk20.cardholder.core.domain.location.ArrivalHandler
import io.github.danyk20.cardholder.core.domain.location.LocationAccess
import io.github.danyk20.cardholder.core.domain.usecase.ArrivalSuggestion
import io.github.danyk20.cardholder.core.domain.usecase.ArrivalSuggestionUseCase
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.feature.carddetail.navigation.cardDetailDeepLink
import io.github.danyk20.cardholder.quickaccess.showCodeIntent
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Notifies with the right card when the user stays at a place where they used it. The card's name is
 * hidden on the lock screen, and never shown at all for a locked card.
 */
class ArrivalNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val arrivals: ArrivalSuggestionUseCase,
    private val locationAccess: LocationAccess,
) : ArrivalHandler {
    private val manager = NotificationManagerCompat.from(context)

    override suspend fun onArrival(placeIds: List<Long>) {
        val suggestion = arrivals.onArrival(placeIds) ?: return
        // Checked again right before posting: the user may have just revoked it.
        if (!locationAccess.canNotify()) return
        createChannel()
        @Suppress("MissingPermission") // Checked above.
        manager.notify(tagOf(suggestion.card.id), NOTIFICATION_ID, notificationFor(suggestion))
    }

    override suspend fun onDeparture(placeIds: List<Long>) {
        arrivals.onDeparture(placeIds).forEach { manager.cancel(tagOf(it), NOTIFICATION_ID) }
    }

    private fun notificationFor(suggestion: ArrivalSuggestion): Notification {
        val card = suggestion.card
        val title = if (card.isLocked) context.getString(R.string.notification_arrival_locked_title) else card.title
        val isLoyalty = card.type == CardType.LOYALTY
        val text = context.getString(
            if (isLoyalty) R.string.notification_arrival_text else R.string.notification_arrival_open,
        )
        val lockScreenVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_arrival_public))
            .build()
        val open = if (isLoyalty) {
            showCodeIntent(context, card)
        } else {
            Intent(context, MainActivity::class.java)
                .setData(cardDetailDeepLink(card.id).toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreenVersion)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    card.id.value.hashCode(),
                    open,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .addAction(
                0,
                context.getString(R.string.notification_arrival_not_here),
                ArrivalActionReceiver.notThisPlace(context, card.id, suggestion.placeId),
            )
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            // Gone by itself if the user stays longer than any shopping trip.
            .setTimeoutAfter(TimeUnit.HOURS.toMillis(2))
            .build()
    }

    private fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notification_channel_arrival))
                .setDescription(context.getString(R.string.notification_channel_arrival_description))
                .build(),
        )
    }

    companion object {
        private const val CHANNEL_ID = "arrival_suggestions"
        internal const val NOTIFICATION_ID = 2

        internal fun tagOf(cardId: CardId) = "arrival-${cardId.value}"
    }
}
