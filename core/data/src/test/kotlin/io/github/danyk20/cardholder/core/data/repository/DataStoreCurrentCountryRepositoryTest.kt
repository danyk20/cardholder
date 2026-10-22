package io.github.danyk20.cardholder.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.danyk20.cardholder.core.domain.country.CountrySource
import io.github.danyk20.cardholder.core.domain.country.CurrentCountry
import io.github.danyk20.cardholder.core.domain.usecase.ResolveCurrentCountryUseCase
import io.github.danyk20.cardholder.core.model.CountryCode
import io.github.danyk20.cardholder.core.testing.repository.FakeApproximateLocationSource
import io.github.danyk20.cardholder.core.testing.repository.FakeCountryBorders
import io.github.danyk20.cardholder.core.testing.repository.FakeNetworkCountrySource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreCurrentCountryRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val network = FakeNetworkCountrySource()
    private val file by lazy { folder.root.resolve("country_state.preferences_pb") }
    private val ch = requireNotNull(CountryCode.of("CH"))
    private val at = requireNotNull(CountryCode.of("AT"))
    private val de = requireNotNull(CountryCode.of("DE"))

    /** A repository on the test's file, running its DataStore in [scope]. */
    private fun repository(scope: CoroutineScope) = DataStoreCurrentCountryRepository(
        PreferenceDataStoreFactory.create(scope = scope) { file },
        ResolveCurrentCountryUseCase(
            network,
            FakeApproximateLocationSource(),
            FakeCountryBorders(),
            Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneOffset.UTC),
        ),
    )

    @Test
    fun `nothing is in use before anything was detected`() = runTest {
        val repository = repository(backgroundScope)

        val state = repository.state.first()

        assertNull(state.effective)
        assertNull(state.detected)
    }

    @Test
    fun `a refresh detects the country and keeps it`() = runTest {
        val repository = repository(backgroundScope)
        network.country = ch

        repository.refresh()

        assertEquals(CurrentCountry(ch, CountrySource.NETWORK), repository.state.first().effective)
    }

    @Test
    fun `the detected country is there again after a restart`() = runTest {
        val scope = CoroutineScope(Job())
        repository(scope).also { network.country = ch }.refresh()
        scope.coroutineContext[Job]?.cancel()

        val restarted = repository(backgroundScope)

        assertEquals(CurrentCountry(ch, CountrySource.NETWORK), restarted.state.first().detected)
    }

    @Test
    fun `a choice overrides the detected country and is shown as manual`() = runTest {
        val repository = repository(backgroundScope)
        network.country = ch
        repository.refresh()

        repository.setOverride(at)

        val state = repository.state.first()
        assertEquals(CurrentCountry(at, CountrySource.MANUAL), state.effective)
        assertEquals(CurrentCountry(ch, CountrySource.NETWORK), state.detected)
    }

    @Test
    fun `a choice is kept while detection finds the same country`() = runTest {
        val repository = repository(backgroundScope)
        network.country = ch
        repository.refresh()
        repository.setOverride(at)

        repository.refresh()

        assertEquals(at, repository.state.first().effective?.code)
    }

    @Test
    fun `a choice is dropped once detection finds another country`() = runTest {
        val repository = repository(backgroundScope)
        network.country = ch
        repository.refresh()
        repository.setOverride(at)

        network.country = de
        repository.refresh()

        val state = repository.state.first()
        assertEquals(CurrentCountry(de, CountrySource.NETWORK), state.effective)
        assertEquals(false, state.isManual)
    }

    @Test
    fun `clearing a choice goes back to the detected country`() = runTest {
        val repository = repository(backgroundScope)
        network.country = ch
        repository.refresh()
        repository.setOverride(at)

        repository.clearOverride()

        assertEquals(CurrentCountry(ch, CountrySource.NETWORK), repository.state.first().effective)
    }

    @Test
    fun `a choice made before any detection is dropped by the first detection`() = runTest {
        val repository = repository(backgroundScope)
        repository.setOverride(at)
        network.country = ch

        repository.refresh()

        assertEquals(CurrentCountry(ch, CountrySource.NETWORK), repository.state.first().effective)
    }
}
