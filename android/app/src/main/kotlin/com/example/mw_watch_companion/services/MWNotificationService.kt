package com.example.mw_watch_companion.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.*

import com.example.mw_watch_companion.BLE.BleConnectionManager
import com.example.mw_watch_companion.common.*
import com.example.mw_watch_companion.notifications.NotificationManager

class MWNotificationService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var notificationManager: NotificationManager 

    override fun onCreate() {
        super.onCreate()
        Log.i("Notification Service", "Created service")
        notificationManager = NotificationManager(serviceScope) 
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        Log.i("Notification Service", "Notification received ${sbn.toString()}")
        val notification = sbn.notification ?: return
        val extras = notification.extras
        var title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                    ?:extras.getString(Notification.EXTRA_TITLE)
                    ?: ""
        var text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                   ?:extras.getString(Notification.EXTRA_TEXT)
                   ?: ""
        var longTxt = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                      ?:extras.getString(Notification.EXTRA_BIG_TEXT)
                      ?: ""
        var packageName = sbn.packageName
        Log.i("Notification Service", "=============Notification received==============")
        Log.i("Notification Service", "Package : ${packageName}")
        Log.i("Notification Service", "Title : ${title}")
        Log.i("Notification Service", "Text : ${text}")
        Log.i("Notification Service", "Long text : ${longTxt}")
        Log.i("Notification Service", "================================================")
        notificationManager.acceptNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        Log.i("Notification Service", "Notification removed ${sbn.toString()}")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}