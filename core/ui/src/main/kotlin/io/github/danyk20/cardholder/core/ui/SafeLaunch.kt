package io.github.danyk20.cardholder.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Launches ViewModel work whose unexpected failures are passed to [onError] instead of crashing the
 * app. Expected failures are still modelled as results; this is the last line of defence for
 * everything else (storage errors, bugs in edge cases, …). Cancellation is not an error.
 */
fun ViewModel.launchSafely(onError: (Throwable) -> Unit = {}, block: suspend CoroutineScope.() -> Unit): Job =
    viewModelScope.launch(CoroutineExceptionHandler { _, throwable -> onError(throwable) }, block = block)
