package io.github.danyk20.cardholder.core.data.repository

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.danyk20.cardholder.core.database.dao.CardDao
import io.github.danyk20.cardholder.core.domain.di.IoDispatcher
import io.github.danyk20.cardholder.core.domain.repository.AppDataRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class AndroidAppDataRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    // Lazy: creating the database itself throws when its key is gone, which must not happen at injection.
    private val cardDao: Lazy<CardDao>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AppDataRepository {
    @Suppress("TooGenericExceptionCaught") // Any failure means the data can't be opened.
    override suspend fun isStorageAvailable(): Boolean = withContext(ioDispatcher) {
        try {
            // A first query opens the database (and runs migrations) off the main thread.
            cardDao.get().maxPosition()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Encrypted storage can't be opened", e)
            false
        }
    }

    /** Clears files, databases, preferences and Keystore entries of the app, then the system kills it. */
    override fun eraseAllData() {
        context.getSystemService(ActivityManager::class.java).clearApplicationUserData()
    }

    private companion object {
        const val TAG = "AppData"
    }
}
