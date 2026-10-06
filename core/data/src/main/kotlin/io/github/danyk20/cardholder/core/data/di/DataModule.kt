package io.github.danyk20.cardholder.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.danyk20.cardholder.core.data.repository.AssetShopRepository
import io.github.danyk20.cardholder.core.data.repository.DataStoreUserPreferencesRepository
import io.github.danyk20.cardholder.core.data.repository.LocaleCountryRepository
import io.github.danyk20.cardholder.core.data.repository.OfflineCardImageRepository
import io.github.danyk20.cardholder.core.data.repository.OfflineCardRepository
import io.github.danyk20.cardholder.core.domain.di.ApplicationScope
import io.github.danyk20.cardholder.core.domain.di.IoDispatcher
import io.github.danyk20.cardholder.core.domain.repository.CardImageRepository
import io.github.danyk20.cardholder.core.domain.repository.CardRepository
import io.github.danyk20.cardholder.core.domain.repository.CountryRepository
import io.github.danyk20.cardholder.core.domain.repository.ShopRepository
import io.github.danyk20.cardholder.core.domain.repository.UserPreferencesRepository
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {
    @Binds
    fun bindsCardRepository(impl: OfflineCardRepository): CardRepository

    @Binds
    fun bindsCardImageRepository(impl: OfflineCardImageRepository): CardImageRepository

    @Binds
    fun bindsShopRepository(impl: AssetShopRepository): ShopRepository

    @Binds
    fun bindsCountryRepository(impl: LocaleCountryRepository): CountryRepository

    @Binds
    fun bindsUserPreferencesRepository(impl: DataStoreUserPreferencesRepository): UserPreferencesRepository

    companion object {
        @Provides
        fun providesClock(): Clock = Clock.systemUTC()

        @Provides
        @IoDispatcher
        fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @Singleton
        @ApplicationScope
        fun providesApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Provides
        @Singleton
        fun providesPreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("user_preferences") }
    }
}
