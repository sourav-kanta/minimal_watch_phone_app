package com.example.mw_watch_companion.notifications

import android.service.notification.StatusBarNotification
import com.example.mw_watch_companion.BLE.BleConnectionManager
import kotlinx.coroutines.*

import android.util.Log

class NotificationManager (private val scope: CoroutineScope){

    private val notificationTTL = 3*60*1000L
    private val deDuplicator = NotificationDeduplicator(scope, notificationTTL) 
    
    fun acceptNotification(sbn: StatusBarNotification) {
        val handler = HandlerRegistry.handlers.find { it.canHandle(sbn) } ?: DefaultHandler()
        val processedNotification = handler.process(sbn)?: return
        Log.i("NotificationManager", "Forwarding notification ${processedNotification.toString()}")
        // Push to pipeline
        val payload = processedNotification.toUByteArray()
        if(!deDuplicator.checkDuplicateAndUpdateCache(deDuplicator.generateCRC(payload))) {
            BleConnectionManager.forwardNotification(payload)
        }
        else {
            Log.i("NotificationManager", "Avoid pushing duplicate")
        }
    }
}