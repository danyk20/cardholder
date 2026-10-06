package io.github.danyk20.cardholder.core.domain.di

import javax.inject.Qualifier

/** Qualifies the [kotlinx.coroutines.CoroutineDispatcher] used for blocking I/O and crypto work. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher

/** Qualifies the application-wide [kotlinx.coroutines.CoroutineScope] that outlives screens. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
