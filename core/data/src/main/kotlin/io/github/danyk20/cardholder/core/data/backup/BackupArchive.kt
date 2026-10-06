package io.github.danyk20.cardholder.core.data.backup

import io.github.danyk20.cardholder.core.data.model.CardDetailsDto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Decrypted content of a backup: card data plus the JPEG photos referenced by name. */
internal class BackupContent(val cards: List<BackupCard>, val images: Map<String, ByteArray>)

@Serializable
internal data class BackupManifest(val formatVersion: Int, val exportedAt: Long, val cards: List<BackupCard>)

@Serializable
internal data class BackupCard(
    val id: String,
    val type: String,
    val title: String,
    val color: String,
    val isLocked: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val country: String? = null,
    val shopId: String? = null,
    val shopName: String? = null,
    val barcodeFormat: String? = null,
    val bankId: String? = null,
    val bankName: String? = null,
    val details: CardDetailsDto,
    val cvv: String? = null,
    val frontImage: String? = null,
    val backImage: String? = null,
    val logoImage: String? = null,
    val position: Int = 0,
) {
    override fun toString(): String = "BackupCard(id=$id, type=$type)"
}

/** Zip container of a backup: `cards.json` plus `images/<name>.jpg`. */
internal object BackupArchive {
    private const val FORMAT_VERSION = 1
    private const val MANIFEST = "cards.json"
    private const val IMAGE_DIR = "images/"
    private const val MAX_ENTRY_BYTES = 32L * 1024 * 1024
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    fun write(content: BackupContent, exportedAt: Long): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            val manifest = BackupManifest(FORMAT_VERSION, exportedAt, content.cards)
            zip.write(json.encodeToString(BackupManifest.serializer(), manifest).encodeToByteArray())
            zip.closeEntry()
            content.images.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(IMAGE_DIR + name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    fun read(bytes: ByteArray): BackupContent {
        var manifest: BackupManifest? = null
        val images = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val data = zip.readBounded()
                when {
                    entry.name == MANIFEST -> manifest = runCatching {
                        json.decodeFromString(BackupManifest.serializer(), data.decodeToString())
                    }.getOrElse { throw InvalidBackupException("Malformed card data", it) }

                    entry.name.startsWith(IMAGE_DIR) && isSafeName(entry.name.removePrefix(IMAGE_DIR)) ->
                        images[entry.name.removePrefix(IMAGE_DIR)] = data
                }
            }
        }
        val cards = manifest?.cards ?: throw InvalidBackupException("Backup has no card data")
        return BackupContent(cards, images)
    }

    private fun isSafeName(name: String) = name.isNotEmpty() && name.none { it == '/' || it == '\\' } && name != ".."

    /** Guards against zip bombs: a single entry may not exceed [MAX_ENTRY_BYTES]. */
    private fun ZipInputStream.readBounded(): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_ENTRY_BYTES) throw InvalidBackupException("Backup entry too large")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
