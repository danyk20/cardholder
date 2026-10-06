package io.github.danyk20.cardholder

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import dagger.hilt.android.HiltAndroidApp
import io.github.danyk20.cardholder.core.domain.di.ApplicationScope
import io.github.danyk20.cardholder.core.domain.repository.CardImageRepository
import io.github.danyk20.cardholder.core.security.ProcessSessionLockEvents
import io.github.danyk20.cardholder.image.CardImageFetcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class CardholderApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject
    lateinit var sessionLockEvents: ProcessSessionLockEvents

    @Inject
    lateinit var cardImageRepository: CardImageRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        sessionLockEvents.register()
        applicationScope.launch {
            // Decrypted photos must not outlive the session in memory.
            sessionLockEvents.events.collect {
                SingletonImageLoader.get(this@CardholderApplication).memoryCache?.clear()
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
}
