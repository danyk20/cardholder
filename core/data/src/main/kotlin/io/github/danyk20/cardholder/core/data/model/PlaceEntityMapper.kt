package io.github.danyk20.cardholder.core.data.model

import io.github.danyk20.cardholder.core.database.model.PlaceAccessPointEntity
import io.github.danyk20.cardholder.core.database.model.PlaceCentre
import io.github.danyk20.cardholder.core.database.model.PlaceEntity
import io.github.danyk20.cardholder.core.database.model.PlaceFixEntity
import io.github.danyk20.cardholder.core.database.model.PlaceWithAccessPoints
import io.github.danyk20.cardholder.core.database.model.VisitAccessPoint
import io.github.danyk20.cardholder.core.database.model.VisitFix
import io.github.danyk20.cardholder.core.database.model.VisitWrite
import io.github.danyk20.cardholder.core.domain.location.AccessPoint
import io.github.danyk20.cardholder.core.domain.location.Bssid
import io.github.danyk20.cardholder.core.domain.location.Centre
import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.domain.location.LocationFix
import io.github.danyk20.cardholder.core.domain.location.Place
import io.github.danyk20.cardholder.core.domain.location.VisitPlan
import io.github.danyk20.cardholder.core.model.CardId
import java.time.Instant

internal fun PlaceWithAccessPoints.toPlace(): Place = place.toPlace(accessPoints.map { it.toAccessPoint() })

internal fun PlaceEntity.toPlace(accessPoints: List<AccessPoint> = emptyList()): Place = Place(
    id = id,
    cardId = CardId(cardId),
    centre = latitude?.let { lat -> longitude?.let { lon -> GeoPoint(lat, lon) } },
    accuracyMeters = accuracyMeters,
    useCount = useCount,
    lastUsedAt = Instant.ofEpochMilli(lastUsedAt),
    lastNotifiedAt = lastNotifiedAt?.let(Instant::ofEpochMilli),
    accessPoints = accessPoints,
)

private fun PlaceAccessPointEntity.toAccessPoint() = AccessPoint(Bssid(bssid), ssid, rssi)

internal fun PlaceFixEntity.toFix() =
    LocationFix(GeoPoint(latitude, longitude), accuracyMeters, Instant.ofEpochMilli(recordedAt))

internal fun Centre.toColumns() = PlaceCentre(point.latitude, point.longitude, accuracyMeters)

internal fun VisitPlan.toWrite(cardId: CardId, now: Instant, keepFixes: Int, keepAccessPoints: Int) = VisitWrite(
    cardId = cardId.value,
    placeId = placeId,
    sameVisit = sameVisit,
    fix = fix?.let { VisitFix(it.point.latitude, it.point.longitude, it.accuracyMeters, it.time.toEpochMilli()) },
    accessPoints = accessPoints.map { VisitAccessPoint(it.bssid.value, it.ssid, it.rssi) },
    travelling = travelling.map { it.value },
    now = now.toEpochMilli(),
    keepFixes = keepFixes,
    keepAccessPoints = keepAccessPoints,
)
