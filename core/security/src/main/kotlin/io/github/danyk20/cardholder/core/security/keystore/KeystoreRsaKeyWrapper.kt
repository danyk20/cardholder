package io.github.danyk20.cardholder.core.security.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import io.github.danyk20.cardholder.core.security.AuthenticationRequiredException
import io.github.danyk20.cardholder.core.security.DeviceNotSecureException
import io.github.danyk20.cardholder.core.security.KeyInvalidatedException
import io.github.danyk20.cardholder.core.security.KeyWrapper
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * Wraps data keys with an RSA-OAEP key pair whose private key requires user authentication.
 *
 * Wrapping uses only the public key and therefore never needs authentication, so new secrets (a CVV,
 * a newly locked card) can be stored without prompting. Unwrapping needs the private key, which the
 * Keystore only releases for [AUTH_VALIDITY_SECONDS] after a strong biometric or device credential
 * authentication.
 */
internal class KeystoreRsaKeyWrapper(private val alias: String) : KeyWrapper {
    override fun wrap(dataKey: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, softwarePublicKey(getOrCreateUsableKeyPair()), OAEP_SPEC)
        }
        return cipher.doFinal(dataKey)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        val privateKey = androidKeyStore().getKey(alias, null) as? PrivateKey ?: throw KeyInvalidatedException()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SPEC)
        } catch (e: UserNotAuthenticatedException) {
            throw AuthenticationRequiredException(e)
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw KeyInvalidatedException(e)
        }
        return try {
            cipher.doFinal(wrapped)
        } catch (e: GeneralSecurityException) {
            throw KeyInvalidatedException(e)
        }
    }

    /**
     * Returns the public key, (re)creating the key pair when it is missing or permanently invalidated.
     * Data protected by an invalidated key cannot be recovered anyway.
     */
    @Synchronized
    private fun getOrCreateUsableKeyPair(): PublicKey {
        val keyStore = androidKeyStore()
        val privateKey = keyStore.getKey(alias, null) as? PrivateKey
        if (privateKey != null && isUsable(privateKey)) return keyStore.getCertificate(alias).publicKey
        keyStore.deleteEntry(alias)
        return generateKeyPair()
    }

    private fun isUsable(privateKey: PrivateKey): Boolean = try {
        Cipher.getInstance(TRANSFORMATION).init(Cipher.DECRYPT_MODE, privateKey, OAEP_SPEC)
        true
    } catch (_: UserNotAuthenticatedException) {
        true
    } catch (_: KeyPermanentlyInvalidatedException) {
        false
    }

    private fun generateKeyPair(): PublicKey = try {
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEY_STORE).apply {
            initialize(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(KEY_BITS)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationParameters(
                        AUTH_VALIDITY_SECONDS,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                    )
                    // Enrolling another fingerprint must not destroy the user's data.
                    .setInvalidatedByBiometricEnrollment(false)
                    .build(),
            )
        }.generateKeyPair().public
    } catch (e: IllegalStateException) {
        throw DeviceNotSecureException(e)
    } catch (e: java.security.InvalidAlgorithmParameterException) {
        throw DeviceNotSecureException(e)
    }

    /** Keystore public keys are restricted to the key's purposes; a software copy can always encrypt. */
    private fun softwarePublicKey(key: PublicKey): PublicKey =
        KeyFactory.getInstance(key.algorithm).generatePublic(X509EncodedKeySpec(key.encoded))

    companion object {
        /** How long the private key stays usable after the user authenticated. */
        const val AUTH_VALIDITY_SECONDS = 30

        private const val KEY_BITS = 3072
        private const val TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

        // Android Keystore only supports SHA-1 for MGF1, so it is set explicitly on both sides.
        private val OAEP_SPEC =
            OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
    }
}
