package io.github.danyk20.cardholder.core.domain.security

import kotlinx.coroutines.flow.Flow

/** Device-level security state relevant to protecting cards. */
interface DeviceSecurity {
    /** `true` if a screen lock (PIN, pattern, password) is set; required for CVVs and locked cards. */
    fun isDeviceSecure(): Boolean
}

/** Emits whenever decrypted data held in memory must be discarded, e.g. when the app goes to the background. */
interface SessionLockEvents {
    val events: Flow<Unit>
}

/** Clipboard for sensitive values: hidden from previews and cleared automatically after a short time. */
interface SecureClipboard {
    fun copy(label: String, text: String)
}
