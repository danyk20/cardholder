package io.github.danyk20.cardholder.core.security.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.danyk20.cardholder.core.domain.security.DeviceSecurity
import io.github.danyk20.cardholder.core.domain.security.ScreenCapturePolicy
import io.github.danyk20.cardholder.core.domain.security.SecureClipboard
import io.github.danyk20.cardholder.core.domain.security.SessionLockEvents
import io.github.danyk20.cardholder.core.security.AndroidDeviceSecurity
import io.github.danyk20.cardholder.core.security.DatabasePassphraseProvider
import io.github.danyk20.cardholder.core.security.EnvelopeCipher
import io.github.danyk20.cardholder.core.security.InMemoryScreenCapturePolicy
import io.github.danyk20.cardholder.core.security.ProcessSessionLockEvents
import io.github.danyk20.cardholder.core.security.ProtectionLevel
import io.github.danyk20.cardholder.core.security.SensitiveClipboard
import io.github.danyk20.cardholder.core.security.keystore.KeystoreAesKeyWrapper
import io.github.danyk20.cardholder.core.security.keystore.KeystoreRsaKeyWrapper
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface SecurityModule {
    @Binds
    fun bindsDeviceSecurity(impl: AndroidDeviceSecurity): DeviceSecurity

    @Binds
    fun bindsSecureClipboard(impl: SensitiveClipboard): SecureClipboard

    @Binds
    fun bindsSessionLockEvents(impl: ProcessSessionLockEvents): SessionLockEvents

    @Binds
    fun bindsScreenCapturePolicy(impl: InMemoryScreenCapturePolicy): ScreenCapturePolicy

    companion object {
        @Provides
        @Singleton
        fun providesEnvelopeCipher(): EnvelopeCipher = EnvelopeCipher(
            mapOf(
                ProtectionLevel.STANDARD to KeystoreAesKeyWrapper(alias = "cardholder.standard.v1"),
                ProtectionLevel.PROTECTED to KeystoreRsaKeyWrapper(alias = "cardholder.protected.v1"),
            ),
        )

        @Provides
        @Singleton
        fun providesDatabasePassphraseProvider(
            @ApplicationContext context: Context,
            cipher: EnvelopeCipher,
        ): DatabasePassphraseProvider =
            DatabasePassphraseProvider(File(context.noBackupFilesDir, "database.key"), cipher)
    }
}
