package io.github.danyk20.cardholder.core.data.backup

import io.github.danyk20.cardholder.core.data.model.CardDetailsDto
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class BackupArchiveTest {
    private val card = BackupCard(
        id = "1",
        type = "LOYALTY",
        title = "Coffee",
        color = "BROWN",
        isLocked = false,
        createdAt = 1,
        updatedAt = 2,
        shopName = "Corner Coffee",
        barcodeFormat = "QR_CODE",
        details = CardDetailsDto.Loyalty("12345"),
        frontImage = "a.img",
    )

    @Test
    fun `round trips cards and images`() {
        val bytes = BackupArchive.write(BackupContent(listOf(card), mapOf("a.img" to byteArrayOf(1, 2, 3))), 99)

        val content = BackupArchive.read(bytes)

        assertEquals(listOf(card), content.cards)
        assertContentEquals(byteArrayOf(1, 2, 3), content.images.getValue("a.img"))
    }

    @Test
    fun `ignores entries trying to escape the images folder`() {
        val bytes = BackupArchive.write(BackupContent(listOf(card), emptyMap()), 99)
        val withEvilEntry = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                ZipInputStream(bytes.inputStream()).use { source ->
                    generateSequence { source.nextEntry }.forEach { entry ->
                        zip.putNextEntry(ZipEntry(entry.name))
                        zip.write(source.readBytes())
                    }
                }
                zip.putNextEntry(ZipEntry("images/../../evil"))
                zip.write(byteArrayOf(6))
            }
        }.toByteArray()

        assertTrue(BackupArchive.read(withEvilEntry).images.isEmpty())
    }

    @Test
    fun `archive without card data is invalid`() {
        val empty = ByteArrayOutputStream().also { ZipOutputStream(it).use { zip -> zip.putNextEntry(ZipEntry("x")) } }

        assertFailsWith<InvalidBackupException> { BackupArchive.read(empty.toByteArray()) }
    }

    @Test
    fun `a file from a newer version is read and flagged`() {
        val newer =
            archiveWithManifest(manifestJson().withFormatVersion(2).replace("\"title\"", "\"futureField\":1,\"title\""))

        val content = BackupArchive.read(newer)

        assertEquals(listOf(card), content.cards)
        assertTrue(content.isFromNewerVersion)
        assertFalse(BackupArchive.read(archiveWithManifest(manifestJson())).isFromNewerVersion)
    }

    @Test
    fun `unreadable cards from a newer version are reported as such`() {
        val unknownDetails = manifestJson().replace(
            Regex(""""details":\{"type":"[^"]*""""),
            """"details":{"type":"future"""",
        )

        assertFailsWith<NewerBackupVersionException> {
            BackupArchive.read(archiveWithManifest(unknownDetails.withFormatVersion(2)))
        }
        assertFailsWith<InvalidBackupException> { BackupArchive.read(archiveWithManifest(unknownDetails)) }
    }

    /** The `cards.json` this version writes for [card]. */
    private fun manifestJson(): String {
        val bytes = BackupArchive.write(BackupContent(listOf(card), emptyMap()), 99)
        return ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.first { it.name == "cards.json" }
            zip.readBytes().decodeToString()
        }
    }

    private fun String.withFormatVersion(version: Int) =
        replace(Regex(""""formatVersion":\d+"""), """"formatVersion":$version""")

    private fun archiveWithManifest(json: String): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("cards.json"))
            zip.write(json.encodeToByteArray())
        }
    }.toByteArray()
}
