package io.github.danyk20.cardholder.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.danyk20.cardholder.core.database.CardholderDatabase
import io.github.danyk20.cardholder.core.database.dao.CardDao
import io.github.danyk20.cardholder.core.security.DatabasePassphraseProvider
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(
        @ApplicationContext context: Context,
        passphraseProvider: DatabasePassphraseProvider,
    ): CardholderDatabase {
        System.loadLibrary("sqlcipher")
        // The whole database file is encrypted with SQLCipher (AES-256); the key lives in the Keystore.
        val factory = SupportOpenHelperFactory(passphraseProvider.passphrase())
        return Room.databaseBuilder(context, CardholderDatabase::class.java, CardholderDatabase.NAME)
            .openHelperFactory(factory)
            .build()
    }

    @Provides
    fun providesCardDao(database: CardholderDatabase): CardDao = database.cardDao()
}
