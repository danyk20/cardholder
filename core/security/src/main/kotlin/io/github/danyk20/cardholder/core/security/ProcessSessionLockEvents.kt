package io.github.danyk20.cardholder.core.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.danyk20.cardholder.core.domain.security.SessionLockEvents
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Emits when the whole app moves to the background, so screens drop decrypted data. */
@Singleton
class ProcessSessionLockEvents @Inject constructor() : SessionLockEvents {
    private val _events =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<Unit> = _events.asSharedFlow()

    /** Must be called once from the main thread, e.g. in `Application.onCreate`. */
    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    _events.tryEmit(Unit)
                }
            },
        )
    }
}
