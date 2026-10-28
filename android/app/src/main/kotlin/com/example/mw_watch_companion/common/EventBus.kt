
package com.example.mw_watch_companion.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class EventBus(
    bufferCapacity: Int = 64
) {
    @PublishedApi
    internal val events = MutableSharedFlow<Any>(
        replay = 0,
        extraBufferCapacity = bufferCapacity
    )

    suspend fun publish(event: Any) {
        events.emit(event)
    }

    fun tryPublish(event: Any): Boolean {
        return events.tryEmit(event)
    }

    inline fun <reified T : Any> subscribe(
        scope: CoroutineScope,
        noinline handler: suspend (T) -> Unit
    ): Job {
        return events
            .filterIsInstance<T>()
            .onEach { handler(it) }
            .launchIn(scope)
    }
}
