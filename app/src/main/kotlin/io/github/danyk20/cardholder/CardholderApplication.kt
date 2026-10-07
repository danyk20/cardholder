package io.github.danyk20.cardholder

import android.app.Application
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import io.github.danyk20.cardholder.core.domain.di.ApplicationScope
import io.github.danyk20.cardholder.core.domain.repository.CardImageRepository
import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.security.ProcessSessionLockEvents
import io.github.danyk20.cardholder.image.CardImageFetcher
import io.github.danyk20.cardholder.quickaccess.CardShortcuts
import io.github.danyk20.cardholder.quickaccess.CardsWidget
import io.github.danyk20.cardholder.reminders.ExpiryReminderWorker
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@HiltAndroidApp
class CardholderApplication :
    Application(),
    SingletonImageLoader.Factory,
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    @Inject
    lateinit var sessionLockEvents: ProcessSessionLockEvents

    @Inject
    lateinit var cardImageRepository: CardImageRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    // Lazy: opening the card database must not happen on the main thread or crash the app's start.
    @Inject
    lateinit var cardRepository: Lazy<CardRepository>

    @Inject
    lateinit var cardShortcuts: CardShortcuts

    override fun onCreate() {
        super.onCreate()
        sessionLockEvents.register()
        ExpiryReminderWorker.schedule(this)
        keepQuickAccessUpToDate()
        applicationScope.launch {
            // Decrypted photos must not outlive the session in memory.
            sessionLockEvents.events.collect {
                SingletonImageLoader.get(this@CardholderApplication).memoryCache?.clear()
            }
        }
    }

    /** App shortcuts and the widget follow the cards: favourites, usage, renames, deletions. */
    private fun keepQuickAccessUpToDate() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                cardRepository.get().observeCards().distinctUntilChanged().collect { cards ->
                    cardShortcuts.update(cards)
                    CardsWidget().updateAll(this@CardholderApplication)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // The cards can't be read (see the recovery screen); shortcuts just stay as they are.
                Log.w(TAG, "Couldn't update shortcuts and widget", e)
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(CardImageFetcher.Factory(cardImageRepository))
            add(CardImageFetcher.RefKeyer())
        }
        // Card photos are already stored encrypted; never write decrypted copies to a disk cache.
        .diskCachePolicy(CachePolicy.DISABLED)
        .build()

    private companion object {
        const val TAG = "Cardholder"
    }
}
