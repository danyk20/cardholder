package io.github.danyk20.cardholder.core.storage

import io.github.danyk20.cardholder.core.security.EnvelopeCipher
import io.github.danyk20.cardholder.core.security.ProtectionLevel
import java.io.File
import java.io.FileNotFoundException

/** Stores small binary blobs in [directory], each sealed with an [EnvelopeCipher]. Writes are atomic. */
class EncryptedFileStore(private val directory: File, private val cipher: EnvelopeCipher) {
    fun write(name: String, plaintext: ByteArray, level: ProtectionLevel) {
        writeAtomically(file(name), cipher.seal(plaintext, level))
    }

    fun read(name: String): ByteArray = cipher.open(sealedBytes(name))

    fun levelOf(name: String): ProtectionLevel = cipher.levelOf(sealedBytes(name))

    /** Re-encrypts an existing entry with [level]; reading a [ProtectionLevel.PROTECTED] entry needs authentication. */
    fun reseal(name: String, level: ProtectionLevel) {
        val sealed = sealedBytes(name)
        if (cipher.levelOf(sealed) == level) return
        val plaintext = cipher.open(sealed)
        try {
            writeAtomically(file(name), cipher.seal(plaintext, level))
        } finally {
            plaintext.fill(0)
        }
    }

    fun delete(name: String) {
        file(name).delete()
    }

    fun exists(name: String): Boolean = file(name).exists()

    private fun sealedBytes(name: String): ByteArray {
        val file = file(name)
        if (!file.exists()) throw FileNotFoundException("No entry $name")
        return file.readBytes()
    }

    private fun file(name: String): File {
        require(name.isNotBlank() && name.none { it == '/' || it == '\\' } && name != "." && name != "..") {
            "Invalid entry name"
        }
        return File(directory, name)
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        directory.mkdirs()
        val tmp = File(directory, "${target.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            error("Could not write ${target.name}")
        }
    }
}
