package io.github.danyk20.cardholder.core.security

/** Wraps (encrypts) and unwraps per-message data encryption keys with a long-lived key. */
interface KeyWrapper {
    /** @throws DeviceNotSecureException if the wrapping key cannot be created. */
    fun wrap(dataKey: ByteArray): ByteArray

    /**
     * @throws AuthenticationRequiredException if the wrapping key needs a recent user authentication.
     * @throws KeyInvalidatedException if the wrapping key is gone or cannot decrypt [wrapped] anymore.
     */
    fun unwrap(wrapped: ByteArray): ByteArray
}
