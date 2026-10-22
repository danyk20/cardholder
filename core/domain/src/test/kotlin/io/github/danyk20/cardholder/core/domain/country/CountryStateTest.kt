package io.github.danyk20.cardholder.core.domain.country

import io.github.danyk20.cardholder.core.model.CountryCode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class CountryStateTest {
    private val ch = requireNotNull(CountryCode.of("CH"))
    private val de = requireNotNull(CountryCode.of("DE"))
    private val detected = CurrentCountry(ch, CountrySource.NETWORK)

    @Test
    fun `the detected country is in use without a manual choice`() {
        val state = StoredCountry(detected = detected).toState()

        assertEquals(detected, state.effective)
        assertEquals(detected, state.detected)
        assertFalse(state.isManual)
    }

    @Test
    fun `a manual choice is in use and the detected country is kept apart`() {
        val state = StoredCountry(detected = detected, override = de, overrideBasis = ch).toState()

        assertEquals(CurrentCountry(de, CountrySource.MANUAL), state.effective)
        assertEquals(detected, state.detected)
        assertTrue(state.isManual)
    }

    @Test
    fun `nothing is in use when nothing was detected or chosen`() {
        val state = StoredCountry().toState()

        assertNull(state.effective)
        assertFalse(state.isManual)
    }
}
