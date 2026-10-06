package io.github.danyk20.cardholder.core.security

import io.github.danyk20.cardholder.core.domain.model.SecureResult
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.junit.Test

class EnvelopeCipherTest {
    private val standard = FakeKeyWrapper()
    private val protected = FakeKeyWrapper(requiresAuthentication = true)
    private val cipher = fakeEnvelopeCipher(standard, protected)
    private val plaintext = "4111111111111111".encodeToByteArray()

    @Test
    fun `round trips standard data`() {
        val sealed = cipher.seal(plaintext, ProtectionLevel.STANDARD)

        assertContentEquals(plaintext, cipher.open(sealed))
        assertEquals(ProtectionLevel.STANDARD, cipher.levelOf(sealed))
    }

    @Test
    fun `sealed data does not contain the plaintext`() {
        val sealed = cipher.seal(plaintext, ProtectionLevel.STANDARD)

        assertFalse(sealed.decodeToString(throwOnInvalidSequence = false).contains("4111111111111111"))
    }

    @Test
    fun `sealing the same plaintext twice gives different output`() {
        assertFalse(
            cipher.seal(plaintext, ProtectionLevel.STANDARD)
                .contentEquals(cipher.seal(plaintext, ProtectionLevel.STANDARD)),
        )
    }

    @Test
    fun `protected data can be sealed without authentication but opened only with it`() {
        val sealed = cipher.seal(plaintext, ProtectionLevel.PROTECTED)

        assertEquals(SecureResult.AuthenticationRequired, secureCall { cipher.open(sealed) })
        protected.isAuthenticated = true
        assertContentEquals(plaintext, cipher.open(sealed))
    }

    @Test
    fun `invalidated key is reported`() {
        val sealed = cipher.seal(plaintext, ProtectionLevel.PROTECTED)
        protected.isInvalidated = true

        assertEquals(SecureResult.KeyInvalidated, secureCall { cipher.open(sealed) })
    }

    @Test
    fun `tampering with the ciphertext is detected`() {
        val sealed = cipher.seal(plaintext, ProtectionLevel.STANDARD)
        sealed[sealed.lastIndex] = (sealed.last() + 1).toByte()

        assertFailsWith<CorruptedDataException> { cipher.open(sealed) }
    }

    @Test
    fun `tampering with the protection level is detected`() {
        protected.isAuthenticated = true
        val sealed = cipher.seal(plaintext, ProtectionLevel.STANDARD)
        sealed[1] = ProtectionLevel.PROTECTED.id

        assertFailsWith<Exception> { cipher.open(sealed) }
    }

    @Test
    fun `rejects malformed input`() {
        assertFailsWith<CorruptedDataException> { cipher.open(byteArrayOf(1, 1)) }
        assertFailsWith<CorruptedDataException> { cipher.open(ByteArray(64).also { it[0] = 9 }) }
    }
}
