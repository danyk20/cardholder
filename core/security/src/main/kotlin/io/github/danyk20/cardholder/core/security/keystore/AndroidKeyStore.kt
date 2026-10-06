package io.github.danyk20.cardholder.core.security.keystore

import java.security.KeyStore

internal const val ANDROID_KEY_STORE = "AndroidKeyStore"

internal fun androidKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
