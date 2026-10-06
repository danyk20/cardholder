package io.github.danyk20.cardholder.core.security.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import io.github.danyk20.cardholder.core.security.KeyInvalidatedException
import io.github.danyk20.cardholder.core.security.KeyWrapper
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps data keys with a hardware-backed AES-256-GCM key that can be used without user interaction.
 * Wrapped format: `iv[12] | ciphertext+tag`.
 */
internal class KeystoreAesKeyWrapper(private val alias: String) : KeyWrapper {
    override fun wrap(dataKey: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        return cipher.iv + cipher.doFinal(dataKey)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        val key = existingKey() ?: throw KeyInvalidatedException()
        return try {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, wrapped, 0, IV_BYTES))
            }.doFinal(wrapped, IV_BYTES, wrapped.size - IV_BYTES)
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw KeyInvalidatedException(e)
        } catch (e: GeneralSecurityException) {
            // The data was wrapped by a key that no longer exists.
            throw KeyInvalidatedException(e)
        }
    }

    private fun existingKey(): SecretKey? = androidKeyStore().getKey(alias, null) as? SecretKey

    @Synchronized
    private fun getOrCreateKey(): SecretKey = existingKey() ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(KEY_BITS)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
        }
        .generateKey()

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
