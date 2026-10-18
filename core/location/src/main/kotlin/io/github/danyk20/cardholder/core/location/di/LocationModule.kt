package io.github.danyk20.cardholder.core.location.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.danyk20.cardholder.core.domain.location.GeofenceRegistrar
import io.github.danyk20.cardholder.core.domain.location.LocationAccess
import io.github.danyk20.cardholder.core.domain.location.SurroundingsProvider
import io.github.danyk20.cardholder.core.location.AndroidLocationAccess
import io.github.danyk20.cardholder.core.location.AndroidSurroundingsProvider
import io.github.danyk20.cardholder.core.location.geofence.PlayServicesGeofenceRegistrar

@Module
@InstallIn(SingletonComponent::class)
internal interface LocationModule {
    @Binds
    fun bindsLocationAccess(impl: AndroidLocationAccess): LocationAccess

    @Binds
    fun bindsSurroundingsProvider(impl: AndroidSurroundingsProvider): SurroundingsProvider

    @Binds
    fun bindsGeofenceRegistrar(impl: PlayServicesGeofenceRegistrar): GeofenceRegistrar
}
