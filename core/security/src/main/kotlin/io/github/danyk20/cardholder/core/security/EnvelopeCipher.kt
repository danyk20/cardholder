package io.github.danyk20.cardholder.core.security

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Envelope encryption: every message is encrypted with a fresh random AES-256-GCM data key, which
 * is itself wrapped by the [KeyWrapper] of the requested [ProtectionLevel].
 *
 * Sealed format (all integers big-endian):
 * ```
 * version:u8 | level:u8 | wrappedKeyLength:u16 | wrappedKey | iv[12] | ciphertext+tag
 * ```
 * The version and level bytes are authenticated as associated data.
 */
class EnvelopeCipher(
    private val wrappers: Map<ProtectionLevel, KeyWrapper>,
    private val random: SecureRandom = SecureRandom(),
) {
    fun seal(plaintext: ByteArray, level: ProtectionLevel): ByteArray {
        val dataKey = ByteArray(DATA_KEY_BYTES).also(random::nextBytes)
        try {
            val wrappedKey = wrapper(level).wrap(dataKey)
            val iv = ByteArray(IV_BYTES).also(random::nextBytes)
            val header = byteArrayOf(VERSION, level.id)
            val ciphertext = aesGcm(Cipher.ENCRYPT_MODE, dataKey, iv, header).doFinal(plaintext)
            return ByteBuffer.allocate(header.size + Short.SIZE_BYTES + wrappedKey.size + iv.size + ciphertext.size)
                .put(header)
                .putShort(wrappedKey.size.toShort())
                .put(wrappedKey)
                .put(iv)
                .put(ciphertext)
                .array()
        } finally {
            dataKey.fill(0)
        }
    }

    fun open(sealed: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(sealed)
        val (header, level) = readHeader(buffer)
        val wrappedKey = ByteArray(buffer.readLength()).also { buffer.getOrThrow(it) }
        val iv = ByteArray(IV_BYTES).also { buffer.getOrThrow(it) }
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val dataKey = wrapper(level).unwrap(wrappedKey)
        try {
            return aesGcm(Cipher.DECRYPT_MODE, dataKey, iv, header).doFinal(ciphertext)
        } catch (e: GeneralSecurityException) {
            throw CorruptedDataException("Authentication tag mismatch", e)
        } finally {
            dataKey.fill(0)
        }
    }

    /** Reads only the protection level of [sealed] data without decrypting it. */
    fun levelOf(sealed: ByteArray): ProtectionLevel = readHeader(ByteBuffer.wrap(sealed)).second

    private fun readHeader(buffer: ByteBuffer): Pair<ByteArray, ProtectionLevel> {
        if (buffer.remaining() < MIN_SIZE) throw CorruptedDataException("Sealed data too short")
        val header = ByteArray(HEADER_BYTES).also(buffer::get)
        if (header[0] != VERSION) throw CorruptedDataException("Unsupported version ${header[0]}")
        return header to ProtectionLevel.fromId(header[1])
    }

    private fun ByteBuffer.readLength(): Int = short.toInt() and UNSIGNED_SHORT_MASK

    private fun ByteBuffer.getOrThrow(target: ByteArray) {
        if (remaining() < target.size) throw CorruptedDataException("Sealed data truncated")
        get(target)
    }

    private fun wrapper(level: ProtectionLevel): KeyWrapper =
        wrappers[level] ?: error("No key wrapper configured for $level")

    private fun aesGcm(mode: Int, key: ByteArray, iv: ByteArray, aad: ByteArray): Cipher =
        Cipher.getInstance(AES_GCM).apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            updateAAD(aad)
        }

    private companion object {
        const val VERSION: Byte = 1
        const val AES_GCM = "AES/GCM/NoPadding"
        const val DATA_KEY_BYTES = 32
        const val IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val HEADER_BYTES = 2
        const val UNSIGNED_SHORT_MASK = 0xFFFF
        const val MIN_SIZE = HEADER_BYTES + Short.SIZE_BYTES + IV_BYTES
    }
}
