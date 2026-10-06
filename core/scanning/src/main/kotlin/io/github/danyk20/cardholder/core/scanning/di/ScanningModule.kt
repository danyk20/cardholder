package io.github.danyk20.cardholder.core.scanning.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.danyk20.cardholder.core.domain.repository.BarcodeImageScanner
import io.github.danyk20.cardholder.core.scanning.MlKitBarcodeImageScanner

@Module
@InstallIn(SingletonComponent::class)
internal interface ScanningModule {
    @Binds
    fun bindsBarcodeImageScanner(impl: MlKitBarcodeImageScanner): BarcodeImageScanner
}
