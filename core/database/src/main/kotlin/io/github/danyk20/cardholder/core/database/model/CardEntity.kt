package io.github.danyk20.cardholder.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A stored card. Plain columns hold only non-sensitive data needed to list and search cards;
 * the sensitive details and the CVV are stored as sealed envelopes.
 */
@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val id: String,
    /** `BANK`, `ID` or `LOYALTY`. */
    val type: String,
    val title: String,
    val color: String,
    @ColumnInfo(name = "is_locked") val isLocked: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "bank_network") val bankNetwork: String?,
    @ColumnInfo(name = "id_country") val idCountry: String?,
    @ColumnInfo(name = "loyalty_shop_id") val loyaltyShopId: String?,
    @ColumnInfo(name = "loyalty_shop_name") val loyaltyShopName: String?,
    @ColumnInfo(name = "loyalty_barcode_format") val loyaltyBarcodeFormat: String?,
    @ColumnInfo(name = "front_image") val frontImage: String?,
    @ColumnInfo(name = "back_image") val backImage: String?,
    @ColumnInfo(name = "logo_image", defaultValue = "NULL") val logoImage: String? = null,
    @ColumnInfo(name = "position", defaultValue = "0") val position: Int = 0,
    /** Sealed JSON of the type-specific details. */
    @ColumnInfo(name = "sealed_details", typeAffinity = ColumnInfo.BLOB) val sealedDetails: ByteArray,
    /** Sealed CVV of a bank card, always protected by user authentication. */
    @ColumnInfo(name = "sealed_cvv", typeAffinity = ColumnInfo.BLOB) val sealedCvv: ByteArray?,
) {
    @Suppress("CyclomaticComplexMethod")
    override fun equals(other: Any?): Boolean = this === other ||
        (
            other is CardEntity &&
                id == other.id &&
                type == other.type &&
                title == other.title &&
                color == other.color &&
                isLocked == other.isLocked &&
                createdAt == other.createdAt &&
                updatedAt == other.updatedAt &&
                bankNetwork == other.bankNetwork &&
                idCountry == other.idCountry &&
                loyaltyShopId == other.loyaltyShopId &&
                loyaltyShopName == other.loyaltyShopName &&
                loyaltyBarcodeFormat == other.loyaltyBarcodeFormat &&
                frontImage == other.frontImage &&
                backImage == other.backImage &&
                logoImage == other.logoImage &&
                position == other.position &&
                sealedDetails.contentEquals(other.sealedDetails) &&
                sealedCvv.contentEquals(other.sealedCvv)
            )

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "CardEntity(id=$id, type=$type, isLocked=$isLocked)"
}
