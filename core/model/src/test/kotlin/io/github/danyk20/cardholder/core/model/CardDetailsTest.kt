package io.github.danyk20.cardholder.core.model

import java.time.YearMonth
import kotlin.test.assertFalse
import org.junit.Test

class CardDetailsTest {
    @Test
    fun `toString never leaks sensitive values`() {
        val details = CardDetails.Bank("4111111111111111", YearMonth.of(2030, 1), "JANE DOE")
        val text = details.toString()
        assertFalse("4111" in text)
        assertFalse("JANE" in text)
        assertFalse("SECRET" in CardDetails.Loyalty("SECRET").toString())
    }
}
