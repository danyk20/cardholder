package io.github.danyk20.cardholder.core.testing.repository

import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.domain.location.LocationFix
import io.github.danyk20.cardholder.core.domain.usecase.CardPlaces
import io.github.danyk20.cardholder.core.domain.usecase.FindNearbyCardsUseCase
import io.github.danyk20.cardholder.core.domain.usecase.RememberPlaceUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Suggestions by location wired to fakes for ViewModel tests: a precise location is allowed and the
 * sensors find a fix, but the feature is off until a test changes [preferences].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestPlaces(
    val cards: FakeCardRepository,
    val preferences: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
) {
    val places = FakePlaceRepository(cards)
    val sensors = FakeSurroundingsProvider().apply { fix = LocationFix(GeoPoint(48.1486, 17.1077), 10f, Now) }
    val access = FakeLocationAccess()
    val shops = FakeShopRepository()

    val rememberPlace = RememberPlaceUseCase(
        cardRepository = cards,
        placeRepository = places,
        preferencesRepository = preferences,
        surroundings = sensors,
        locationAccess = access,
        applicationScope = CoroutineScope(UnconfinedTestDispatcher()),
    )
    val findNearbyCards = FindNearbyCardsUseCase(
        cardRepository = cards,
        placeRepository = places,
        shopRepository = shops,
        preferencesRepository = preferences,
        surroundings = sensors,
        locationAccess = access,
        clock = Clock.fixed(Now, ZoneOffset.UTC),
    )
    val cardPlaces = CardPlaces(cards, places, preferences, rememberPlace, findNearbyCards)

    private companion object {
        val Now: Instant = Instant.parse("2026-10-18T12:00:00Z")
    }
}
