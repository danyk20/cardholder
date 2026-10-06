package io.github.danyk20.cardholder.core.security

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class InMemoryScreenCapturePolicyTest {
    @Test
    fun `starts protected and can be allowed and revoked`() {
        val policy = InMemoryScreenCapturePolicy()
        assertFalse(policy.isAllowed.value)

        policy.setAllowed(true)
        assertTrue(policy.isAllowed.value)

        policy.setAllowed(false)
        assertFalse(policy.isAllowed.value)
    }
}
