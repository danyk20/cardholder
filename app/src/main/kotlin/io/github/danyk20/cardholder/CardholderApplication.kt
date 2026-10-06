package io.github.danyk20.cardholder

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.danyk20.cardholder.core.security.ProcessSessionLockEvents
import javax.inject.Inject

@HiltAndroidApp
class CardholderApplication : Application() {
    @Inject
    lateinit var sessionLockEvents: ProcessSessionLockEvents

    override fun onCreate() {
        super.onCreate()
        sessionLockEvents.register()
    }
}
