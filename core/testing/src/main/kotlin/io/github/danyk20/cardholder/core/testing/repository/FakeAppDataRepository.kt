package io.github.danyk20.cardholder.core.testing.repository

import io.github.danyk20.cardholder.core.domain.repository.AppDataRepository

class FakeAppDataRepository(var isAvailable: Boolean = true) : AppDataRepository {
    var erased = false
        private set

    override suspend fun isStorageAvailable(): Boolean = isAvailable

    override fun eraseAllData() {
        erased = true
    }
}
