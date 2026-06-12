package com.example.mw_watch_companion.common

import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

class DataQueue<T>(private val queueName: String = "GenericQueue") {

    private val channel = Channel<T>(capacity = 64)

    fun push(item: T) {
        Log.d("MessageQueue", "[$queueName] Message buffered")
        val result = channel.trySend(item)
        if (!result.isSuccess) {
            Log.e("MessageQueue", "[$queueName] Queue full!")
        }
    }

    fun getReceiveChannel(): ReceiveChannel<T> = channel
}