package io.github.danyk20.cardholder.core.data.backup

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The backup file is not a Cardholder backup or is damaged. */
class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The password does not decrypt the backup (or the file was modified). */
class WrongPasswordException(cause: Throwable? = null) : Exception("Wrong password", cause)

/**
 * Password-based encryption of backup files.
 *
 * ```
 * magic "CHBK" | version:u8 | iterations:u32 | salt[16] | iv[12] | AES-256-GCM ciphertext+tag
 * ```
 * The key is derived with PBKDF2-HMAC-SHA256; the whole header is authenticated as associated data.
 */
class BackupCodec(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val random: SecureRandom = SecureRandom(),
) {
    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC)
            .put(VERSION)
            .putInt(iterations)
            .put(salt)
            .put(iv)
            .array()
        val ciphertext = cipher(Cipher.ENCRYPT_MODE, password, salt, iterations, iv, header).doFinal(plaintext)
        return header + ciphertext
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        val header = parseHeader(data)
        return try {
            cipher(Cipher.DECRYPT_MODE, password, header.salt, header.iterations, header.iv, header.bytes)
                .doFinal(data, HEADER_BYTES, data.size - HEADER_BYTES)
        } catch (e: GeneralSecurityException) {
            throw WrongPasswordException(e)
        }
    }

    private class Header(val bytes: ByteArray, val iterations: Int, val salt: ByteArray, val iv: ByteArray)

    private fun parseHeader(data: ByteArray): Header {
        ensureValid(data.size >= HEADER_BYTES + TAG_BYTES) { "File too short" }
        val buffer = ByteBuffer.wrap(data)
        val magic = ByteArray(MAGIC.size).also(buffer::get)
        ensureValid(magic.contentEquals(MAGIC)) { "Not a Cardholder backup" }
        val version = buffer.get()
        ensureValid(version == VERSION) { "Unsupported backup version $version" }
        val iterations = buffer.int
        ensureValid(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Invalid KDF parameters" }
        val salt = ByteArray(SALT_BYTES).also(buffer::get)
        val iv = ByteArray(IV_BYTES).also(buffer::get)
        return Header(data.copyOfRange(0, HEADER_BYTES), iterations, salt, iv)
    }

    private inline fun ensureValid(condition: Boolean, message: () -> String) {
        if (!condition) throw InvalidBackupException(message())
    }

    private fun cipher(
        mode: Int,
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        iv: ByteArray,
        header: ByteArray,
    ): Cipher {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        val keyBytes = try {
            SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return try {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(mode, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(TAG_BYTES * Byte.SIZE_BITS, iv))
                updateAAD(header)
            }
        } finally {
            keyBytes.fill(0)
        }
    }

    companion object {
        /** OWASP recommendation for PBKDF2-HMAC-SHA256 (2023). */
        const val DEFAULT_ITERATIONS = 600_000

        private val MAGIC = "CHBK".encodeToByteArray()
        private const val VERSION: Byte = 1
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16
        private const val KEY_BITS = 256
        private const val MIN_ITERATIONS = 1_000
        private const val MAX_ITERATIONS = 10_000_000
        private const val KDF = "PBKDF2WithHmacSHA256"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val HEADER_BYTES = MAGIC.size + 1 + Int.SIZE_BYTES + SALT_BYTES + IV_BYTES
    }
}
