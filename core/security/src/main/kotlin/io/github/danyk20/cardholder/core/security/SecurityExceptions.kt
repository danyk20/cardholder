package io.github.danyk20.cardholder.core.security

import io.github.danyk20.cardholder.core.domain.model.SecureResult
import kotlin.coroutines.cancellation.CancellationException

/** The data is protected and the user must authenticate before it can be decrypted. */
class AuthenticationRequiredException(cause: Throwable? = null) : Exception("User authentication required", cause)

/** The key protecting the data is permanently gone (e.g. the screen lock was removed). */
class KeyInvalidatedException(cause: Throwable? = null) : Exception("Protection key permanently invalidated", cause)

/** Protected data cannot be created because the device has no secure lock screen. */
class DeviceNotSecureException(cause: Throwable? = null) : Exception("A secure lock screen is required", cause)

/** Encrypted data is malformed or was tampered with. */
class CorruptedDataException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Runs [block] and maps protection failures to the corresponding [SecureResult]. Any other failure
 * becomes [SecureResult.Failed], so storage and crypto errors never crash the app. Cancellation is
 * passed on.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> secureCall(block: () -> T): SecureResult<T> = try {
    SecureResult.Success(block())
} catch (_: AuthenticationRequiredException) {
    SecureResult.AuthenticationRequired
} catch (_: KeyInvalidatedException) {
    SecureResult.KeyInvalidated
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    SecureResult.Failed(e)
} catch (e: OutOfMemoryError) {
    // E.g. decoding a huge photo; the allocation failed, so the app can carry on.
    SecureResult.Failed(e)
}
