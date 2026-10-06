package io.github.danyk20.cardholder.core.security

import android.app.KeyguardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.danyk20.cardholder.core.domain.security.DeviceSecurity
import javax.inject.Inject

internal class AndroidDeviceSecurity @Inject constructor(@ApplicationContext private val context: Context) :
    DeviceSecurity {
    override fun isDeviceSecure(): Boolean = context.getSystemService(KeyguardManager::class.java).isDeviceSecure
}
