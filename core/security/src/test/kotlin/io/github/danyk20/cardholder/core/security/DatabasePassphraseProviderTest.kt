package io.github.danyk20.cardholder.core.security

import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatabasePassphraseProviderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val cipher = fakeEnvelopeCipher()

    @Test
    fun `generates a passphrase once and returns the same one afterwards`() {
        val file = File(folder.root, "keys/database.key")

        val first = DatabasePassphraseProvider(file, cipher).passphrase()
        val second = DatabasePassphraseProvider(file, cipher).passphrase()

        assertEquals(32, first.size)
        assertContentEquals(first, second)
    }

    @Test
    fun `stores the passphrase sealed`() {
        val file = File(folder.root, "database.key")
        val passphrase = DatabasePassphraseProvider(file, cipher).passphrase()

        assertFalse(file.readBytes().contentEquals(passphrase))
        assertEquals(ProtectionLevel.STANDARD, cipher.levelOf(file.readBytes()))
    }
}
