package io.github.danyk20.cardholder.core.security

import java.io.File
import java.security.SecureRandom

/**
 * Provides the random passphrase of the encrypted database. The passphrase is generated once and
 * stored sealed with a [ProtectionLevel.STANDARD] key, so it never touches the disk in plain text.
 */
class DatabasePassphraseProvider(
    private val file: File,
    private val cipher: EnvelopeCipher,
    private val random: SecureRandom = SecureRandom(),
) {
    @Synchronized
    fun passphrase(): ByteArray {
        if (file.exists()) return cipher.open(file.readBytes())
        val passphrase = ByteArray(PASSPHRASE_BYTES).also(random::nextBytes)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeBytes(cipher.seal(passphrase, ProtectionLevel.STANDARD))
        check(tmp.renameTo(file)) { "Could not persist database key" }
        return passphrase
    }

    private companion object {
        const val PASSPHRASE_BYTES = 32
    }
}
