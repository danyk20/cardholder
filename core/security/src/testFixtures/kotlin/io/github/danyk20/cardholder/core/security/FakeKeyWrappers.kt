package io.github.danyk20.cardholder.core.security

import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** In-memory stand-in for a Keystore-backed [KeyWrapper]. */
class FakeKeyWrapper(var requiresAuthentication: Boolean = false) : KeyWrapper {
    var isAuthenticated = false
    var isInvalidated = false
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().encoded

    override fun wrap(dataKey: ByteArray): ByteArray {
        val iv = ByteArray(12) { it.toByte() }
        return iv + cipher(Cipher.ENCRYPT_MODE, iv).doFinal(dataKey)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        if (isInvalidated) throw KeyInvalidatedException()
        if (requiresAuthentication && !isAuthenticated) throw AuthenticationRequiredException()
        return cipher(Cipher.DECRYPT_MODE, wrapped.copyOf(12)).doFinal(wrapped, 12, wrapped.size - 12)
    }

    private fun cipher(mode: Int, iv: ByteArray) = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
    }
}

fun fakeEnvelopeCipher(
    standard: FakeKeyWrapper = FakeKeyWrapper(),
    protected: FakeKeyWrapper = FakeKeyWrapper(requiresAuthentication = true),
) = EnvelopeCipher(mapOf(ProtectionLevel.STANDARD to standard, ProtectionLevel.PROTECTED to protected))
