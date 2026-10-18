package io.github.danyk20.cardholder.places

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.danyk20.cardholder.core.domain.location.ArrivalHandler

@Module
@InstallIn(SingletonComponent::class)
internal interface PlacesModule {
    @Binds
    fun bindsArrivalHandler(impl: ArrivalNotifier): ArrivalHandler
}
