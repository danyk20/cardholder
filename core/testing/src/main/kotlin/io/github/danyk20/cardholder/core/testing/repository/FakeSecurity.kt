package io.github.danyk20.cardholder.core.testing.repository

import io.github.danyk20.cardholder.core.domain.security.DeviceSecurity
import io.github.danyk20.cardholder.core.domain.security.SessionLockEvents
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeDeviceSecurity(var isSecure: Boolean = true) : DeviceSecurity {
    override fun isDeviceSecure(): Boolean = isSecure
}

class FakeSessionLockEvents : SessionLockEvents {
    override val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun lock() {
        events.tryEmit(Unit)
    }
}
