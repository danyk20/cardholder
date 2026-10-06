package io.github.danyk20.cardholder.core.domain.repository

/** The app's encrypted storage as a whole. */
interface AppDataRepository {
    /**
     * Whether the encrypted database can be opened. `false` e.g. when the device lost the app's
     * Keystore keys (some OEMs wipe them after a security reset); the data is then unrecoverable.
     */
    suspend fun isStorageAvailable(): Boolean

    /** Permanently erases all cards, photos, settings and keys. The system closes the app afterwards. */
    fun eraseAllData()
}
