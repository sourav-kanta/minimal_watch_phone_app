package com.example.mw_watch_companion.common

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import java.util.concurrent.atomic.AtomicBoolean

class DataConsumer<T>(
    private val targetQueue: DataQueue<T>,
    private val ingestor: DataIngestor<T>,
    private val consumerName: String = "GenericConsumer"
) {
    private var job:Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private val isPaused = AtomicBoolean(false)
    private var suspensionGate = CompletableDeferred<Unit>()

    fun start() {
        if(job?.isActive == true) return
        job = scope.launch {
            Log.i("DataConsumer", "[$consumerName] Worker started.")
            for(item in targetQueue.getReceiveChannel()) { 
                if (isPaused.get()) {
                    Log.d("MessageConsumer", "[$consumerName] Stream paused. Parking coroutine...")
                    suspensionGate.await()
                    Log.d("MessageConsumer", "[$consumerName] Coroutine unparked. Processing data.")
                }
                try {
                    Log.d("MessageConsumer", "[$consumerName] Ingesting new message")
                    ingestor.ingest(item)
                } catch (e: Exception) {
                    Log.e("DataConsumer", "[$consumerName] Error", e)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        Log.i("MessageConsumer", "Initiated job cancelletion")
    }

    fun pause() {
        if (isPaused.compareAndSet(false, true)) {
            Log.w("MessageConsumer", "[$consumerName] Gate closed. Incoming items will buffer in channel.")
            suspensionGate = CompletableDeferred()
        }
    }

    fun resume() {
        if (isPaused.compareAndSet(true, false)) {
            Log.i("MessageConsumer", "[$consumerName] Gate opened. Releasing pipeline.")
            suspensionGate.complete(Unit)
        }
    }
}