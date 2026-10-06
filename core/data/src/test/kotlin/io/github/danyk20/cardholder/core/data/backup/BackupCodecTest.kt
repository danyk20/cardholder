package io.github.danyk20.cardholder.core.data.backup

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.junit.Test

class BackupCodecTest {
    private val codec = BackupCodec(iterations = 1_000)
    private val plaintext = "cards".repeat(100).encodeToByteArray()
    private val password = "correct horse battery staple".toCharArray()

    @Test
    fun `round trips with the right password`() {
        assertContentEquals(plaintext, codec.decrypt(codec.encrypt(plaintext, password), password))
    }

    @Test
    fun `output does not contain the plaintext and starts with the magic`() {
        val encrypted = codec.encrypt(plaintext, password)

        assertContentEquals("CHBK".encodeToByteArray(), encrypted.copyOf(4))
        assertFalse(encrypted.decodeToString(throwOnInvalidSequence = false).contains("cardscards"))
    }

    @Test
    fun `wrong password is detected`() {
        val encrypted = codec.encrypt(plaintext, password)

        assertFailsWith<WrongPasswordException> { codec.decrypt(encrypted, "wrong".toCharArray()) }
    }

    @Test
    fun `tampering with header or body is detected`() {
        val encrypted = codec.encrypt(plaintext, password)
        val tamperedBody = encrypted.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        val tamperedSalt = encrypted.copyOf().also { it[12] = (it[12] + 1).toByte() }

        assertFailsWith<WrongPasswordException> { codec.decrypt(tamperedBody, password) }
        assertFailsWith<WrongPasswordException> { codec.decrypt(tamperedSalt, password) }
    }

    @Test
    fun `other files are rejected`() {
        assertFailsWith<InvalidBackupException> {
            codec.decrypt("PK\u0003\u0004 not a backup".repeat(5).encodeToByteArray(), password)
        }
        assertFailsWith<InvalidBackupException> { codec.decrypt(ByteArray(3), password) }
    }
}
