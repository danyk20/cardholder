package io.github.danyk20.cardholder.reminders

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.danyk20.cardholder.core.domain.model.ExpiryReminder
import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.domain.repository.UserPreferencesRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Once a day, on the device: notifies about bank cards and IDs that expire within a month, and about
 * IDs seven months ahead (see [ExpiryReminder.TRAVEL]). Each reminder fires once per expiry date; if
 * the user enters a new date, it fires again.
 */
@HiltWorker
class ExpiryReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val cardRepository: CardRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val notifier: ExpiryNotifier,
    private val clock: Clock,
) : CoroutineWorker(context, params) {
    @Suppress("TooGenericExceptionCaught") // E.g. the database can't be opened; try again tomorrow.
    override suspend fun doWork(): Result = try {
        if (preferencesRepository.preferences.first().expiryReminders) remind()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Expiry reminder check failed", e)
        Result.success()
    }

    private suspend fun remind() {
        cardRepository.fillMissingExpiryDates()
        val today = LocalDate.now(clock.withZone(ZoneId.systemDefault()))
        ExpiryReminder.entries.forEach { reminder ->
            cardRepository.cardsDueForExpiryReminder(reminder, today).forEach { card ->
                val expiresOn = card.expiresOn ?: return@forEach
                // Without permission to notify, keep it pending so it shows once notifications are allowed.
                if (notifier.notify(card, expiresOn, reminder)) {
                    cardRepository.markExpiryReminded(card.id, expiresOn, reminder)
                }
            }
        }
    }

    companion object {
        private const val TAG = "ExpiryReminders"
        private const val WORK_NAME = "expiry-reminders"

        /** Schedules the daily check; keeps an existing schedule. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ExpiryReminderWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
