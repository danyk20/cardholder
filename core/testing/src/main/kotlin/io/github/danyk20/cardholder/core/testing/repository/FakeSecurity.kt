package io.github.danyk20.cardholder.core.testing.repository

import io.github.danyk20.cardholder.core.domain.security.DeviceSecurity
import io.github.danyk20.cardholder.core.domain.security.ScreenCapturePolicy
import io.github.danyk20.cardholder.core.domain.security.SecureClipboard
import io.github.danyk20.cardholder.core.domain.security.SessionLockEvents
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeDeviceSecurity(var isSecure: Boolean = true) : DeviceSecurity {
    override fun isDeviceSecure(): Boolean = isSecure
}

class FakeSessionLockEvents : SessionLockEvents {
    override val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun lock() {
        events.tryEmit(Unit)
    }
}

class FakeScreenCapturePolicy : ScreenCapturePolicy {
    override val isAllowed = MutableStateFlow(false)

    override fun setAllowed(allowed: Boolean) {
        isAllowed.value = allowed
    }
}

class FakeSecureClipboard : SecureClipboard {
    val copied = mutableListOf<Pair<String, String>>()

    override fun copy(label: String, text: String) {
        copied += label to text
    }
}
