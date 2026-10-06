package io.github.danyk20.cardholder.core.storage

import io.github.danyk20.cardholder.core.security.AuthenticationRequiredException
import io.github.danyk20.cardholder.core.security.EnvelopeCipher
import io.github.danyk20.cardholder.core.security.KeyWrapper
import io.github.danyk20.cardholder.core.security.ProtectionLevel
import java.io.File
import java.io.FileNotFoundException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EncryptedFileStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val protectedWrapper = XorKeyWrapper(requiresAuthentication = true)
    private val cipher = EnvelopeCipher(
        mapOf(ProtectionLevel.STANDARD to XorKeyWrapper(), ProtectionLevel.PROTECTED to protectedWrapper),
    )
    private val store by lazy { EncryptedFileStore(File(folder.root, "images"), cipher) }
    private val content = "jpeg-bytes".encodeToByteArray()

    @Test
    fun `writes encrypted and reads back`() {
        store.write("a.img", content, ProtectionLevel.STANDARD)

        assertContentEquals(content, store.read("a.img"))
        assertFalse(File(folder.root, "images/a.img").readBytes().contentEquals(content))
    }

    @Test
    fun `reseal changes the protection level`() {
        store.write("a.img", content, ProtectionLevel.STANDARD)

        store.reseal("a.img", ProtectionLevel.PROTECTED)

        assertEquals(ProtectionLevel.PROTECTED, store.levelOf("a.img"))
        assertFailsWith<AuthenticationRequiredException> { store.read("a.img") }
        protectedWrapper.isAuthenticated = true
        assertContentEquals(content, store.read("a.img"))
    }

    @Test
    fun `delete removes the entry`() {
        store.write("a.img", content, ProtectionLevel.STANDARD)

        store.delete("a.img")

        assertFalse(store.exists("a.img"))
        assertFailsWith<FileNotFoundException> { store.read("a.img") }
    }

    @Test
    fun `rejects path traversal`() {
        assertFailsWith<IllegalArgumentException> { store.write("../evil", content, ProtectionLevel.STANDARD) }
        assertFailsWith<IllegalArgumentException> { store.read("..") }
    }

    /** Trivial wrapper; the real ones are covered by instrumented tests. */
    private class XorKeyWrapper(private val requiresAuthentication: Boolean = false) : KeyWrapper {
        var isAuthenticated = false

        override fun wrap(dataKey: ByteArray) = dataKey.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()

        override fun unwrap(wrapped: ByteArray): ByteArray {
            if (requiresAuthentication && !isAuthenticated) throw AuthenticationRequiredException()
            return wrap(wrapped)
        }
    }
}
