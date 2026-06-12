package com.example.mw_watch_companion.notifications

import android.service.notification.StatusBarNotification
import com.example.mw_watch_companion.common.MWNotification
import com.example.mw_watch_companion.BLE.BleConnectionManager

import android.util.Log

class NotificationManager {
    
    fun acceptNotification(sbn: StatusBarNotification) {
        val handler = HandlerRegistry.handlers.find { it.canHandle(sbn) } ?: DefaultHandler()
        var processedNotification = handler.process(sbn)?: return
        Log.i("NotificationManager", "Forwarding notification ${processedNotification.toString()}")
        // Push to pipeline
        BleConnectionManager.forwardNotification(processedNotification.toUByteArray())
    }
}