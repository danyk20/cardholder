package io.github.danyk20.cardholder.core.data.di

import javax.inject.Qualifier

/** Qualifies the DataStore of the detected country, kept apart from the user's settings. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class CountryStateStore
