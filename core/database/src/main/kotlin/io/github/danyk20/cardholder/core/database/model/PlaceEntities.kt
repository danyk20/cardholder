package io.github.danyk20.cardholder.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Somewhere a card was used. The centre is cached from the place's fixes; `null` if none was accurate enough. */
@Entity(
    tableName = "places",
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["card_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("card_id")],
)
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "card_id") val cardId: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @ColumnInfo(name = "accuracy_m") val accuracyMeters: Float? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long,
    @ColumnInfo(name = "use_count") val useCount: Int = 1,
    @ColumnInfo(name = "last_notified_at") val lastNotifiedAt: Long? = null,
    /** Set by the user on the map: later visits count and add Wi‑Fi, but never move the place. */
    @ColumnInfo(name = "pinned", defaultValue = "0") val pinned: Boolean = false,
)

/** One location fix taken while a card was used at a place; a place keeps only its latest ones. */
@Entity(
    tableName = "place_fixes",
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["place_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("place_id", "recorded_at")],
)
data class PlaceFixEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "place_id") val placeId: Long,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "accuracy_m") val accuracyMeters: Float,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,
)

/**
 * A Wi‑Fi access point seen at a place; [bssid] is its 48-bit hardware address. One the user removed is
 * kept [blocked], so the place doesn't learn it again and the user can restore it.
 */
@Entity(
    tableName = "place_access_points",
    primaryKeys = ["place_id", "bssid"],
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["place_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bssid")],
)
data class PlaceAccessPointEntity(
    @ColumnInfo(name = "place_id") val placeId: Long,
    val bssid: Long,
    val ssid: String?,
    /** Signal strength in dBm when last seen. */
    val rssi: Int,
    /** On how many visits it was seen; the most seen ones are kept. */
    @ColumnInfo(name = "seen_count") val seenCount: Int,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long,
    /** Removed by the user: never a sign of the place, and not counted among the ones it keeps. */
    @ColumnInfo(name = "blocked", defaultValue = "0") val blocked: Boolean = false,
)

/** An access point seen at places far apart (on a bus or train, a phone hotspot); never used again. */
@Entity(tableName = "travelling_access_points")
data class TravellingAccessPointEntity(
    @PrimaryKey val bssid: Long,
    @ColumnInfo(name = "detected_at") val detectedAt: Long,
)

data class PlaceWithAccessPoints(
    @Embedded val place: PlaceEntity,
    @Relation(parentColumn = "id", entityColumn = "place_id") val accessPoints: List<PlaceAccessPointEntity>,
    /** The fixes the centre is computed from, at most [PlaceDao] keeps ten of them. */
    @Relation(parentColumn = "id", entityColumn = "place_id") val fixes: List<PlaceFixEntity> = emptyList(),
)

/** The accuracy-weighted centre of a place's fixes. */
data class PlaceCentre(val latitude: Double, val longitude: Double, val accuracyMeters: Float)

/** One use of a card to store, as planned by the caller. */
data class VisitWrite(
    val cardId: String,
    /** The place to update, or `null` to create one. */
    val placeId: Long?,
    /** The use continues the last visit: the fix only replaces the latest one if more accurate. */
    val sameVisit: Boolean,
    val fix: VisitFix?,
    val accessPoints: List<VisitAccessPoint>,
    val travelling: List<Long>,
    val now: Long,
    val keepFixes: Int,
    val keepAccessPoints: Int,
)

data class VisitFix(val latitude: Double, val longitude: Double, val accuracyMeters: Float, val recordedAt: Long)

data class VisitAccessPoint(val bssid: Long, val ssid: String?, val rssi: Int)
