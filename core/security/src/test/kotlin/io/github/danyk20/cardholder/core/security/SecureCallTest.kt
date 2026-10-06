package io.github.danyk20.cardholder.core.security

import io.github.danyk20.cardholder.core.domain.model.SecureResult
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.junit.Test

class SecureCallTest {
    @Test
    fun `maps protection failures`() {
        assertEquals(SecureResult.Success(1), secureCall { 1 })
        assertEquals(SecureResult.AuthenticationRequired, secureCall { throw AuthenticationRequiredException() })
        assertEquals(SecureResult.KeyInvalidated, secureCall { throw KeyInvalidatedException() })
    }

    @Test
    fun `any other failure becomes Failed instead of crashing`() {
        val failures =
            listOf(
                CorruptedDataException("bad tag"),
                IOException("disk full"),
                NoSuchElementException(),
                OutOfMemoryError(),
            )
        failures.forEach { failure -> assertIs<SecureResult.Failed>(secureCall { throw failure }) }
    }

    @Test
    fun `cancellation is passed on`() {
        assertFailsWith<CancellationException> { secureCall { throw CancellationException() } }
    }
}
