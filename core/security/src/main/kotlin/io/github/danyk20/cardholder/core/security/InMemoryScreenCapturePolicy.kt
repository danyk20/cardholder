package io.github.danyk20.cardholder.core.security

import io.github.danyk20.cardholder.core.domain.security.ScreenCapturePolicy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Never persisted; the activity revokes it when the app leaves the screen. */
@Singleton
class InMemoryScreenCapturePolicy @Inject constructor() : ScreenCapturePolicy {
    private val allowed = MutableStateFlow(false)
    override val isAllowed: StateFlow<Boolean> = allowed.asStateFlow()

    override fun setAllowed(allowed: Boolean) {
        this.allowed.value = allowed
    }
}
