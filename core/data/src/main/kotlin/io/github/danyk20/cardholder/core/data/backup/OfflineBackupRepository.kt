package io.github.danyk20.cardholder.core.data.backup

import android.content.ContentResolver
import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.danyk20.cardholder.core.data.model.toDto
import io.github.danyk20.cardholder.core.data.model.toModel
import io.github.danyk20.cardholder.core.domain.di.IoDispatcher
import io.github.danyk20.cardholder.core.domain.model.BackupResult
import io.github.danyk20.cardholder.core.domain.model.CardContent
import io.github.danyk20.cardholder.core.domain.model.CardDraft
import io.github.danyk20.cardholder.core.domain.model.CvvChange
import io.github.danyk20.cardholder.core.domain.model.ImageChange
import io.github.danyk20.cardholder.core.domain.model.ImageSource
import io.github.danyk20.cardholder.core.domain.model.ImportStrategy
import io.github.danyk20.cardholder.core.domain.model.SecureResult
import io.github.danyk20.cardholder.core.domain.repository.BackupRepository
import io.github.danyk20.cardholder.core.domain.repository.CardImageRepository
import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.domain.security.DeviceSecurity
import io.github.danyk20.cardholder.core.model.BarcodeFormat
import io.github.danyk20.cardholder.core.model.BrandRef
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardColor
import io.github.danyk20.cardholder.core.model.CardDetails
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.model.ImageRef
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Thrown internally when protected data can't be read; mapped to a [BackupResult]. */
private class ProtectedDataException(val result: BackupResult) : Exception()

/**
 * Exports all cards (decrypted, then re-encrypted with the user's password) to a document and imports
 * them again. Works only through the domain repositories, so all on-device encryption rules apply.
 */
internal class OfflineBackupRepository(
    private val cardRepository: CardRepository,
    private val imageRepository: CardImageRepository,
    private val deviceSecurity: DeviceSecurity,
    private val documents: DocumentAccess,
    private val codec: BackupCodec,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
) : BackupRepository {
    @Inject
    constructor(
        cardRepository: CardRepository,
        imageRepository: CardImageRepository,
        deviceSecurity: DeviceSecurity,
        @ApplicationContext context: Context,
        clock: Clock,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        cardRepository = cardRepository,
        imageRepository = imageRepository,
        deviceSecurity = deviceSecurity,
        documents = ContentResolverDocumentAccess(context.contentResolver),
        codec = BackupCodec(),
        clock = clock,
        ioDispatcher = ioDispatcher,
    )

    override suspend fun export(destinationUri: String, password: CharArray): BackupResult = withContext(ioDispatcher) {
        runBackup {
            val cards = cardRepository.observeCards().first()
            val images = mutableMapOf<String, ByteArray>()
            val backupCards = cards.map { card -> card.toBackup(images) }
            val archive = BackupArchive.write(BackupContent(backupCards, images), clock.millis())
            documents.write(destinationUri, codec.encrypt(archive, password))
            BackupResult.Exported(backupCards.size)
        }
    }

    override suspend fun import(sourceUri: String, password: CharArray, strategy: ImportStrategy): BackupResult =
        withContext(ioDispatcher) {
            runBackup {
                val content = BackupArchive.read(codec.decrypt(documents.read(sourceUri), password))
                val needsProtection = content.cards.any { it.isLocked || it.cvv != null }
                if (needsProtection && !deviceSecurity.isDeviceSecure()) return@runBackup BackupResult.DeviceNotSecure
                val existing = cardRepository.observeCards().first().map { it.id.value }.toSet()
                var imported = 0
                var skipped = 0
                // New cards are appended to the custom order, so import them in their original order.
                content.cards.sortedBy { it.position }.forEach { backup ->
                    if (backup.id in existing && strategy == ImportStrategy.SKIP_EXISTING) {
                        skipped++
                    } else {
                        cardRepository.save(backup.toDraft(content.images)).orThrow()
                        imported++
                    }
                }
                BackupResult.Imported(imported = imported, skipped = skipped)
            }
        }

    private suspend fun Card.toBackup(images: MutableMap<String, ByteArray>): BackupCard {
        val details = cardRepository.readDetails(id).orThrow()
        val cvv = if (hasCvv) cardRepository.readCvv(id).orThrow() else null
        suspend fun ImageRef.export(): String {
            images[name] = imageRepository.read(this).orThrow()
            return name
        }
        val info = info
        return BackupCard(
            id = id.value,
            type = type.name,
            title = title,
            color = color.name,
            isLocked = isLocked,
            createdAt = createdAt.toEpochMilli(),
            updatedAt = updatedAt.toEpochMilli(),
            country = (info as? CardInfo.Id)?.country?.value,
            shopId = ((info as? CardInfo.Loyalty)?.shop as? BrandRef.Known)?.id,
            shopName = (info as? CardInfo.Loyalty)?.shop?.name,
            barcodeFormat = (info as? CardInfo.Loyalty)?.format?.name,
            bankId = ((info as? CardInfo.Bank)?.issuer as? BrandRef.Known)?.id,
            bankName = (info as? CardInfo.Bank)?.issuer?.name,
            details = details.toDto(),
            cvv = cvv,
            frontImage = sides.front?.export(),
            backImage = sides.back?.export(),
            logoImage = logo?.export(),
            position = position,
        )
    }

    private fun BackupCard.toDraft(images: Map<String, ByteArray>): CardDraft {
        val details = this.details.toModel()
        val content = when (details) {
            is CardDetails.Bank -> CardContent.Bank(
                details = details,
                cvv = cvv?.let(CvvChange::Set) ?: CvvChange.Remove,
                issuer = bankId?.let { BrandRef.Known(it, bankName.orEmpty()) } ?: bankName?.let(BrandRef::Custom),
            )

            is CardDetails.Id -> CardContent.Id(
                country = country?.let(CountryCode::of) ?: throw InvalidBackupException("ID card without country"),
                details = details,
            )

            is CardDetails.Loyalty -> CardContent.Loyalty(
                shop = shopId?.let { BrandRef.Known(it, shopName.orEmpty()) } ?: BrandRef.Custom(shopName.orEmpty()),
                format = BarcodeFormat.entries.firstOrNull { it.name == barcodeFormat } ?: BarcodeFormat.QR_CODE,
                details = details,
            )
        }
        fun imageChange(name: String?): ImageChange =
            name?.let { images[it] }?.let { ImageChange.Replace(ImageSource.Bytes(it)) } ?: ImageChange.Remove
        return CardDraft(
            id = CardId(id),
            title = title,
            color = CardColor.entries.firstOrNull { it.name == color } ?: CardColor.Default,
            content = content,
            front = imageChange(frontImage),
            back = imageChange(backImage),
            logo = imageChange(logoImage),
            isLocked = isLocked,
        )
    }

    private fun <T> SecureResult<T>.orThrow(): T = when (this) {
        is SecureResult.Success -> value
        SecureResult.AuthenticationRequired -> throw ProtectedDataException(BackupResult.AuthenticationRequired)
        SecureResult.KeyInvalidated -> throw ProtectedDataException(BackupResult.KeyInvalidated)
        is SecureResult.Failed -> throw ProtectedDataException(BackupResult.Failed(cause))
    }

    @Suppress("TooGenericExceptionCaught") // Any unexpected failure is reported to the user, not crashed on.
    private inline fun runBackup(block: () -> BackupResult): BackupResult = try {
        block()
    } catch (e: ProtectedDataException) {
        e.result
    } catch (_: WrongPasswordException) {
        BackupResult.WrongPassword
    } catch (_: InvalidBackupException) {
        BackupResult.InvalidFile
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logFailure(e)
    } catch (e: OutOfMemoryError) {
        // A huge backup file; the allocation failed, so the app can carry on.
        logFailure(e)
    }

    /** Logs only the exception (never card data) to make unexpected failures diagnosable. */
    private fun logFailure(e: Throwable): BackupResult {
        Log.w(TAG, "Backup operation failed", e)
        return BackupResult.Failed(e)
    }

    private companion object {
        const val TAG = "Backup"
    }
}

/** Reads and writes documents chosen by the user through the Storage Access Framework. */
internal interface DocumentAccess {
    fun read(uri: String): ByteArray

    fun write(uri: String, bytes: ByteArray)
}

private class ContentResolverDocumentAccess(private val resolver: ContentResolver) : DocumentAccess {
    override fun read(uri: String): ByteArray =
        resolver.openInputStream(uri.toUri())?.use { it.readBytes() } ?: throw IOException("Cannot open $uri")

    override fun write(uri: String, bytes: ByteArray) {
        // "wt" truncates an existing file instead of leaving stale bytes at the end.
        resolver.openOutputStream(uri.toUri(), "wt")?.use { it.write(bytes) } ?: throw IOException("Cannot open $uri")
    }
}
