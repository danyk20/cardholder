package io.github.danyk20.cardholder.core.domain.location

import io.github.danyk20.cardholder.core.model.CardId
import java.time.Instant

internal val Now: Instant = Instant.parse("2026-10-18T12:00:00Z")

/** Kaufland in Bratislava; one millidegree of latitude is about 111 m. */
internal val Shop = GeoPoint(48.1486, 17.1077)

internal fun GeoPoint.north(meters: Double) = copy(latitude = latitude + meters / METERS_PER_DEGREE)

internal fun fix(point: GeoPoint, accuracy: Float = 10f, time: Instant = Now) = LocationFix(point, accuracy, time)

internal fun ap(bssid: Long, ssid: String? = "net-$bssid", rssi: Int = -60) = AccessPoint(Bssid(bssid), ssid, rssi)

internal fun place(
    id: Long,
    card: String = "card",
    centre: GeoPoint? = Shop,
    accessPoints: List<AccessPoint> = emptyList(),
    useCount: Int = 1,
    lastUsedAt: Instant = Now.minusSeconds(86_400),
) = Place(id, CardId(card), centre, centre?.let { 10f }, useCount, lastUsedAt, accessPoints = accessPoints)

private const val METERS_PER_DEGREE = 111_195.0
