package io.github.danyk20.cardholder.core.domain.usecase

import io.github.danyk20.cardholder.core.domain.country.CountrySource
import io.github.danyk20.cardholder.core.domain.country.CurrentCountry
import io.github.danyk20.cardholder.core.domain.country.StoredCountry
import io.github.danyk20.cardholder.core.domain.location.GeoPoint
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.testing.repository.FakeApproximateLocationSource
import io.github.danyk20.cardholder.core.testing.repository.FakeCountryBorders
import io.github.danyk20.cardholder.core.testing.repository.FakeNetworkCountrySource
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ResolveCurrentCountryUseCaseTest {
    private val now = Instant.parse("2026-10-22T10:00:00Z")
    private val clock = MovableClock(now)
    private val network = FakeNetworkCountrySource()
    private val location = FakeApproximateLocationSource()
    private val borders = FakeCountryBorders()
    private val useCase = ResolveCurrentCountryUseCase(network, location, borders, clock)

    private val point = GeoPoint(47.37, 8.54)
    private val ch = code("CH")
    private val de = code("DE")
    private val at = code("AT")

    @Test
    fun `the mobile network country is used first and location is not read`() = runTest {
        network.country = ch
        location.point = GeoPoint(48.85, 2.35)
        borders.answer = { de }

        val result = useCase(StoredCountry())

        assertEquals(CurrentCountry(ch, CountrySource.NETWORK), result.detected)
        assertEquals(0, location.reads)
    }

    @Test
    fun `without a network country the approximate location is mapped to a country`() = runTest {
        location.point = point
        borders.answer = { if (it == point) de else null }

        val result = useCase(StoredCountry())

        assertEquals(CurrentCountry(de, CountrySource.LOCATION), result.detected)
        assertEquals(listOf(ResolveCurrentCountryUseCase.LOCATION_MAX_AGE), location.requestedMaxAges)
    }

    @Test
    fun `a location outside every country falls back to the last detected country`() = runTest {
        location.point = point
        borders.answer = { null }

        val result = useCase(StoredCountry(detected = CurrentCountry(at, CountrySource.NETWORK)))

        assertEquals(CurrentCountry(at, CountrySource.LAST_KNOWN), result.detected)
    }

    @Test
    fun `without any reading the last detected country is used however old`() = runTest {
        val stored = StoredCountry(detected = CurrentCountry(at, CountrySource.LOCATION))

        val result = useCase(stored)

        assertEquals(CurrentCountry(at, CountrySource.LAST_KNOWN), result.detected)
    }

    @Test
    fun `with nothing ever detected the country is unknown`() = runTest {
        val result = useCase(StoredCountry())

        assertNull(result.detected)
        assertNull(result.override)
    }

    @Test
    fun `the location is looked up at most once within the interval`() = runTest {
        location.point = point
        borders.answer = { de }

        useCase(StoredCountry())
        clock.now = now.plus(Duration.ofMinutes(29))
        val second = useCase(StoredCountry(detected = CurrentCountry(at, CountrySource.LOCATION)))

        assertEquals(1, location.reads)
        assertEquals(CurrentCountry(at, CountrySource.LAST_KNOWN), second.detected)
    }

    @Test
    fun `the location is looked up again once the interval has passed`() = runTest {
        location.point = point
        borders.answer = { de }

        useCase(StoredCountry())
        clock.now = now.plus(ResolveCurrentCountryUseCase.LOCATION_INTERVAL)
        val third = useCase(StoredCountry())

        assertEquals(2, location.reads)
        assertEquals(CurrentCountry(de, CountrySource.LOCATION), third.detected)
    }

    @Test
    fun `a lookup that finds nothing still counts towards the interval`() = runTest {
        location.point = null

        useCase(StoredCountry())
        location.point = point
        borders.answer = { de }
        val result = useCase(StoredCountry())

        assertEquals(1, location.reads)
        assertNull(result.detected)
    }

    @Test
    fun `the network does not count towards the location interval`() = runTest {
        network.country = ch
        useCase(StoredCountry())
        network.country = null
        location.point = point
        borders.answer = { de }

        val result = useCase(StoredCountry())

        assertEquals(CurrentCountry(de, CountrySource.LOCATION), result.detected)
    }

    @Test
    fun `a manual choice is dropped when a fresh detection differs from the country it was made against`() = runTest {
        network.country = ch

        val result = useCase(StoredCountry(override = at, overrideBasis = de))

        assertNull(result.override)
        assertNull(result.overrideBasis)
        assertEquals(CurrentCountry(ch, CountrySource.NETWORK), result.detected)
    }

    @Test
    fun `a manual choice is kept while the fresh detection is the same country`() = runTest {
        network.country = de

        val result = useCase(StoredCountry(override = at, overrideBasis = de))

        assertEquals(at, result.override)
        assertEquals(de, result.overrideBasis)
    }

    @Test
    fun `a manual choice made without a detection is dropped by the first fresh detection`() = runTest {
        network.country = ch

        val result = useCase(StoredCountry(override = at, overrideBasis = null))

        assertNull(result.override)
    }

    @Test
    fun `a manual choice survives a cached result even if the cache differs`() = runTest {
        val stored = StoredCountry(
            detected = CurrentCountry(ch, CountrySource.NETWORK),
            override = at,
            overrideBasis = de,
        )

        val result = useCase(stored)

        assertEquals(at, result.override)
        assertEquals(de, result.overrideBasis)
    }

    @Test
    fun `a manual choice survives an unknown country`() = runTest {
        val result = useCase(StoredCountry(override = at, overrideBasis = de))

        assertEquals(at, result.override)
    }

    private fun code(value: String) = requireNotNull(CountryCode.of(value))

    /** A clock whose time the test moves forward. */
    private class MovableClock(var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId?) = this

        override fun instant(): Instant = now
    }
}
