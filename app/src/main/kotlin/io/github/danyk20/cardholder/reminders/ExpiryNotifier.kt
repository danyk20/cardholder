package io.github.danyk20.cardholder.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.danyk20.cardholder.MainActivity
import io.github.danyk20.cardholder.R
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.feature.carddetail.navigation.cardDetailDeepLink
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

/** Posts expiry reminders. The card's name is hidden on the lock screen. */
class ExpiryNotifier @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /** Returns `false` if notifications aren't allowed, so the reminder can be shown later. */
    fun notify(card: Card, expiresOn: LocalDate): Boolean {
        val permitted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!permitted || !manager.areNotificationsEnabled()) return false
        createChannel()
        val date = expiresOn.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        val open = PendingIntent.getActivity(
            context,
            card.id.value.hashCode(),
            Intent(context, MainActivity::class.java)
                .setData(cardDetailDeepLink(card.id).toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val lockScreenVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_expiry_title))
            .setContentText(context.getString(R.string.notification_expiry_public))
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_expiry_title))
            .setContentText(context.getString(R.string.notification_expiry_text, card.title, date))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreenVersion)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // Checked above.
        manager.notify(card.id.value, NOTIFICATION_ID, notification)
        return true
    }

    private fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notification_channel_expiry))
                .build(),
        )
    }

    private companion object {
        const val CHANNEL_ID = "expiry_reminders"
        const val NOTIFICATION_ID = 1
    }
}
