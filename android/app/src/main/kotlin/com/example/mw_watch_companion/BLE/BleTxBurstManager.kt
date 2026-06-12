package com.example.mw_watch_companion.BLE

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*

class BleBurstManager(private val connectionIntervalMs: Long = BleServiceParams.connInterval, 
                      @Volatile private var responseHandler: BleResponseHandler) {
    private var burstJob: Job? = null
    private val writeWorkAroundDelay = 2L // 2ms delay between writes so Android is happy
    private var burstScope = CoroutineScope(Dispatchers.Default)

    // Our main objective here is to maintain a long radio
    // silence so that link layer can send MD flag as 1 from both sides
    // This will decrease the current window time and save battery on 
    // both sides. We fill the controller buffer in one shot for current
    // connection window and then stay silent, even if this burst drifts over
    // time it doesnt matter as the delay is always connection interval so one 
    // burst per connection window
    fun start() {
        if (burstJob?.isActive == true) return
        
        Log.i("BleBurstManager", "Burst loop starting with drift mitigation.")
        
        burstJob = burstScope.launch {
            // 1. Initial Offset for first window receive
            val startOffset = (connectionIntervalMs * 0.8).toLong()
            delay(startOffset)

            var nextScheduledTime = SystemClock.elapsedRealtime() + connectionIntervalMs

            while (isActive) {
                val packets = responseHandler.getPacketsForBurst()
                packets.forEach { packet ->
                    BleConnectionManager.writeCharacteristic(
                        BleServiceParams.SERVICE_UUID,
                        BleServiceParams.RX_CHAR_UUID,
                        packet
                    )
                    // Workaround to Android Api not providing a callback
                    // when buffer is copied to ACL buffer, so we put a 
                    // small delay before writing next packet and android 
                    // has enough time to copy buffer
                    delay(writeWorkAroundDelay)
                }

                val now = SystemClock.elapsedRealtime()
                val sleepTime = nextScheduledTime - now
                
                if (sleepTime > 0) {
                    delay(sleepTime)
                }

                nextScheduledTime = if (sleepTime < 0) {
                    now + connectionIntervalMs
                } else {
                    nextScheduledTime + connectionIntervalMs
                }
            }
        }
    }

    fun stop() {
        Log.i("BleBurstManager", "Cancelling job")
        burstJob?.cancel()
    }
}