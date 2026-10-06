package io.github.danyk20.cardholder.core.data.repository

import io.github.danyk20.cardholder.core.data.model.decodeCardDetails
import io.github.danyk20.cardholder.core.data.model.encode
import io.github.danyk20.cardholder.core.data.model.toCard
import io.github.danyk20.cardholder.core.data.model.toColumns
import io.github.danyk20.cardholder.core.database.dao.CardDao
import io.github.danyk20.cardholder.core.database.model.CardEntity
import io.github.danyk20.cardholder.core.domain.di.IoDispatcher
import io.github.danyk20.cardholder.core.domain.model.CardContent
import io.github.danyk20.cardholder.core.domain.model.CardDraft
import io.github.danyk20.cardholder.core.domain.model.CvvChange
import io.github.danyk20.cardholder.core.domain.model.ImageChange
import io.github.danyk20.cardholder.core.domain.model.SecureResult
import io.github.danyk20.cardholder.core.domain.model.details
import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.model.Card
import io.github.danyk20.cardholder.core.model.CardDetails
import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CardInfo
import io.github.danyk20.cardholder.core.model.CardNetwork
import io.github.danyk20.cardholder.core.model.ImageRef
import io.github.danyk20.cardholder.core.security.EnvelopeCipher
import io.github.danyk20.cardholder.core.security.ProtectionLevel
import io.github.danyk20.cardholder.core.security.secureCall
import io.github.danyk20.cardholder.core.storage.CardImageStore
import io.github.danyk20.cardholder.core.storage.ImageKind
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [CardRepository] backed by the encrypted Room database and the encrypted image store.
 *
 * Details of unlocked cards are sealed with [ProtectionLevel.STANDARD], details and images of locked
 * cards and every CVV with [ProtectionLevel.PROTECTED].
 */
@Singleton
internal class OfflineCardRepository @Inject constructor(
    private val dao: CardDao,
    private val cipher: EnvelopeCipher,
    private val images: CardImageStore,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CardRepository {
    private val writeMutex = Mutex()

    override fun observeCards(): Flow<List<Card>> = dao.observeAll().map { cards -> cards.map(CardEntity::toCard) }

    override fun observeCard(id: CardId): Flow<Card?> = dao.observe(id.value).map { it?.toCard() }

    override suspend fun readDetails(id: CardId): SecureResult<CardDetails> = withContext(ioDispatcher) {
        secureCall { decodeCardDetails(cipher.open(requireCard(id).sealedDetails)) }
    }

    override suspend fun readCvv(id: CardId): SecureResult<String?> = withContext(ioDispatcher) {
        secureCall { requireCard(id).sealedCvv?.let { cipher.open(it).decodeToString() } }
    }

    override suspend fun save(draft: CardDraft): SecureResult<CardId> = withContext(ioDispatcher) {
        writeMutex.withLock { secureCall { saveCard(draft) } }
    }

    override suspend fun setLocked(id: CardId, locked: Boolean): SecureResult<Unit> = withContext(ioDispatcher) {
        writeMutex.withLock {
            secureCall {
                val card = requireCard(id)
                if (card.isLocked != locked) changeProtection(card, locked)
            }
        }
    }

    override suspend fun delete(id: CardId) = withContext(ioDispatcher) {
        writeMutex.withLock {
            val card = dao.get(id.value) ?: return@withLock
            dao.delete(id.value)
            card.imageRefs().forEach { images.delete(it) }
        }
    }

    private suspend fun saveCard(draft: CardDraft): CardId = withImageTransaction {
        val existing = draft.id?.let { dao.get(it.value) }
        val id = draft.id ?: CardId.random()
        val level = protectionFor(draft.isLocked)
        val front = apply(draft.front, existing?.frontImage?.let(::ImageRef), level)
        val back = apply(draft.back, existing?.backImage?.let(::ImageRef), level)
        // Logos identify the shop like the card title does, so they are never auth-protected.
        val logo = apply(draft.logo, existing?.logoImage?.let(::ImageRef), ProtectionLevel.STANDARD, ImageKind.LOGO)
        val content = draft.content
        val info = content.toInfo()
        val columns = info.toColumns()
        val now = clock.millis()
        dao.upsert(
            CardEntity(
                id = id.value,
                type = info.type.name,
                title = draft.title,
                color = draft.color.name,
                isLocked = draft.isLocked,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                bankNetwork = columns.bankNetwork,
                idCountry = columns.idCountry,
                loyaltyShopId = columns.loyaltyShopId,
                loyaltyShopName = columns.loyaltyShopName,
                loyaltyBarcodeFormat = columns.loyaltyBarcodeFormat,
                frontImage = front?.name,
                backImage = back?.name,
                logoImage = logo?.name,
                sealedDetails = cipher.seal(content.details.encode(), level),
                sealedCvv = content.sealedCvv(existing?.sealedCvv),
            ),
        )
        id
    }

    private suspend fun changeProtection(card: CardEntity, locked: Boolean) = withImageTransaction {
        val level = protectionFor(locked)
        val details = cipher.open(card.sealedDetails)
        try {
            val front = apply(ImageChange.Keep, card.frontImage?.let(::ImageRef), level)
            val back = apply(ImageChange.Keep, card.backImage?.let(::ImageRef), level)
            dao.upsert(
                card.copy(
                    isLocked = locked,
                    updatedAt = clock.millis(),
                    frontImage = front?.name,
                    backImage = back?.name,
                    sealedDetails = cipher.seal(details, level),
                ),
            )
        } finally {
            details.fill(0)
        }
    }

    /** Runs [block], then deletes obsolete images on success or the newly created ones on failure. */
    private suspend fun <T> withImageTransaction(block: suspend ImageTransaction.() -> T): T {
        val transaction = ImageTransaction()
        var succeeded = false
        try {
            return transaction.block().also { succeeded = true }
        } finally {
            withContext(NonCancellable) {
                if (succeeded) transaction.commit() else transaction.rollback()
            }
        }
    }

    private fun CardContent.sealedCvv(current: ByteArray?): ByteArray? = when (this) {
        is CardContent.Bank -> when (val change = cvv) {
            CvvChange.Keep -> current
            CvvChange.Remove -> null
            is CvvChange.Set -> cipher.seal(change.value.encodeToByteArray(), ProtectionLevel.PROTECTED)
        }

        else -> null
    }

    private fun CardContent.toInfo(): CardInfo = when (this) {
        is CardContent.Bank -> CardInfo.Bank(CardNetwork.detect(details.number))
        is CardContent.Id -> CardInfo.Id(country)
        is CardContent.Loyalty -> CardInfo.Loyalty(shop, format)
    }

    private suspend fun requireCard(id: CardId): CardEntity =
        dao.get(id.value) ?: throw NoSuchElementException("No card with id $id")

    private fun CardEntity.imageRefs(): List<ImageRef> = listOfNotNull(frontImage, backImage, logoImage).map(::ImageRef)

    private fun protectionFor(locked: Boolean) = if (locked) ProtectionLevel.PROTECTED else ProtectionLevel.STANDARD

    /**
     * Tracks image files created and made obsolete while saving, so new files can be removed when
     * saving fails and old files only once the database change succeeded.
     */
    private inner class ImageTransaction {
        private val created = mutableListOf<ImageRef>()
        private val obsolete = mutableListOf<ImageRef>()

        suspend fun apply(
            change: ImageChange,
            current: ImageRef?,
            level: ProtectionLevel,
            kind: ImageKind = ImageKind.PHOTO,
        ): ImageRef? = when (change) {
            ImageChange.Keep -> current?.let { reprotect(it, level) }

            ImageChange.Remove -> {
                current?.let(obsolete::add)
                null
            }

            is ImageChange.Replace -> {
                current?.let(obsolete::add)
                images.store(change.source, level, kind).also(created::add)
            }
        }

        private suspend fun reprotect(ref: ImageRef, level: ProtectionLevel): ImageRef {
            if (images.levelOf(ref) == level) return ref
            obsolete += ref
            return images.copy(ref, level).also(created::add)
        }

        suspend fun rollback() = created.forEach { images.delete(it) }

        suspend fun commit() = obsolete.forEach { images.delete(it) }
    }
}
