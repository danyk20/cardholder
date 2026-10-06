package io.github.danyk20.cardholder.core.domain.security

import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the user temporarily allowed screenshots of screens with card data.
 *
 * The permission only lives in memory: it starts disabled and is revoked as soon as the app leaves
 * the screen, so protection can never stay switched off by accident.
 */
interface ScreenCapturePolicy {
    val isAllowed: StateFlow<Boolean>

    fun setAllowed(allowed: Boolean)
}
