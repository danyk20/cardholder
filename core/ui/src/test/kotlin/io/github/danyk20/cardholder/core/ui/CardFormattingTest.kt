package io.github.danyk20.cardholder.core.ui

import java.time.YearMonth
import kotlin.test.assertEquals
import org.junit.Test

class CardFormattingTest {
    @Test
    fun `groups card numbers like they are printed`() {
        assertEquals("4111 1111 1111 1111", formatCardNumber("4111111111111111"))
        assertEquals("3782 822463 10005", formatCardNumber("378282246310005"))
        assertEquals("6759 6498 2643 8453 123", formatCardNumber("6759649826438453123"))
        assertEquals("4111 11", formatCardNumber("411111"))
    }

    @Test
    fun `formats expiry as MM slash YY`() {
        assertEquals("04/30", formatExpiry(YearMonth.of(2030, 4)))
    }
}
