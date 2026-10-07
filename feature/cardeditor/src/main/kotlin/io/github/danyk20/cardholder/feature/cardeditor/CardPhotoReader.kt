package io.github.danyk20.cardholder.feature.cardeditor

import io.github.danyk20.cardholder.core.domain.repository.BarcodeImageScanner
import io.github.danyk20.cardholder.core.domain.repository.CardTextScanner
import io.github.danyk20.cardholder.core.domain.repository.ScannedBarcode
import io.github.danyk20.cardholder.core.domain.scan.CardTextParser
import io.github.danyk20.cardholder.core.model.Bank
import io.github.danyk20.cardholder.core.model.CardType
import io.github.danyk20.cardholder.core.model.Shop
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** Values read from photos of a card; `null` where nothing plausible was found. */
data class PhotoPrefill(
    val number: String? = null,
    val expiry: YearMonth? = null,
    val holder: String? = null,
    val cvv: String? = null,
    val bank: Bank? = null,
    val documentNumber: String? = null,
    val idExpiry: LocalDate? = null,
    val countryCode: String? = null,
    val holderName: String? = null,
    val shop: Shop? = null,
    val barcode: ScannedBarcode? = null,
) {
    override fun toString(): String = "PhotoPrefill(██)"
}

/**
 * Reads card details from photos of its sides: the printed text (on the device) and, for loyalty
 * cards, the barcode. The results are suggestions for empty form fields, never stored on their own.
 */
class CardPhotoReader @Inject constructor(
    private val textScanner: CardTextScanner,
    private val barcodeScanner: BarcodeImageScanner,
) {
    suspend fun read(
        type: CardType,
        front: String?,
        back: String?,
        shops: List<Shop>,
        banks: List<Bank>,
    ): PhotoPrefill {
        val frontLines = front?.let { textScanner.readLines(it) }.orEmpty()
        val backLines = back?.let { textScanner.readLines(it) }.orEmpty()
        val lines = frontLines + backLines
        return when (type) {
            CardType.BANK -> {
                val text = CardTextParser.parseBank(frontLines, backLines, banks.map { it.name })
                val bankName = CardTextParser.findBrand(lines, banks.map { it.name })
                PhotoPrefill(
                    number = text.number,
                    expiry = text.expiry,
                    holder = text.holder,
                    cvv = text.cvv,
                    bank = banks.firstOrNull { it.name == bankName },
                )
            }

            CardType.ID -> CardTextParser.parseMrz(lines)?.let { mrz ->
                PhotoPrefill(
                    documentNumber = mrz.documentNumber,
                    idExpiry = mrz.expiry,
                    countryCode = mrz.issuingCountry,
                    holderName = mrz.holderName,
                )
            } ?: PhotoPrefill()

            CardType.LOYALTY -> {
                val shopName = CardTextParser.findBrand(lines, shops.map { it.name })
                // The code is usually on the back; try it first.
                val barcode = listOfNotNull(back, front).firstNotNullOfOrNull { barcodeScanner.scan(it) }
                PhotoPrefill(shop = shops.firstOrNull { it.name == shopName }, barcode = barcode)
            }
        }
    }
}
