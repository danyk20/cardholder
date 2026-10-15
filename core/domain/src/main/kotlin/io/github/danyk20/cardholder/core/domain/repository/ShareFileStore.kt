package io.github.danyk20.cardholder.core.domain.repository

/** Temporary files handed to other apps when the user shares cards. */
interface ShareFileStore {
    /** Deletes earlier shared files and returns the `content://` URI of a new, empty one. */
    suspend fun newShareFile(): String
}
