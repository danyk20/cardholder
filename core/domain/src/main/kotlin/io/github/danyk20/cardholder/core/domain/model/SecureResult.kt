package io.github.danyk20.cardholder.core.domain.model

/** Outcome of accessing data that may be protected by user authentication. */
sealed interface SecureResult<out T> {
    data class Success<T>(val value: T) : SecureResult<T>

    /** The data is protected and the user has to authenticate (biometric or device credential) first. */
    data object AuthenticationRequired : SecureResult<Nothing>

    /**
     * The protection key was permanently invalidated (e.g. the device screen lock was removed),
     * so the protected data can never be decrypted again.
     */
    data object KeyInvalidated : SecureResult<Nothing>

    /**
     * Anything else went wrong (corrupted data, storage or I/O errors, …). Screens show a generic
     * error instead of crashing; [cause] is for diagnostics only and may contain no user data.
     */
    data class Failed(val cause: Throwable) : SecureResult<Nothing> {
        override fun toString(): String = "Failed(${cause::class.simpleName})"
    }
}

inline fun <T, R> SecureResult<T>.map(transform: (T) -> R): SecureResult<R> = when (this) {
    is SecureResult.Success -> SecureResult.Success(transform(value))
    SecureResult.AuthenticationRequired -> SecureResult.AuthenticationRequired
    SecureResult.KeyInvalidated -> SecureResult.KeyInvalidated
    is SecureResult.Failed -> this
}

fun <T> SecureResult<T>.getOrNull(): T? = (this as? SecureResult.Success)?.value
