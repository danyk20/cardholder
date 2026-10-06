package io.github.danyk20.cardholder.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class CountryCodeTest {
    @Test
    fun `normalizes valid codes`() {
        assertEquals("CH", CountryCode.of(" ch ")?.value)
    }

    @Test
    fun `rejects malformed codes`() {
        listOf("", "C", "CHE", "1A", "Ä1").forEach { assertNull(CountryCode.of(it), it) }
    }

    @Test
    fun `builds flag emoji`() {
        assertEquals("🇨🇭", CountryCode.of("CH")!!.flagEmoji)
    }
}
