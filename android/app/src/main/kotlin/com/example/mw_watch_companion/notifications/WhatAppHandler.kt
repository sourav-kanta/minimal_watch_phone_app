package com.example.mw_watch_companion.notifications

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.example.mw_watch_companion.common.MWNotification
import com.example.mw_watch_companion.common.PhoneApp

class WhatsAppHandler : NotificationHandler {
    override fun canHandle(sbn: StatusBarNotification): Boolean {
        return sbn.packageName.toString().contains("whatsapp", ignoreCase = true)
    }

    override fun process(sbn: StatusBarNotification): MWNotification? {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "WhatsApp"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        
        return MWNotification(
            appId = PhoneApp.WHATSAPP,
            appName = "WhatsApp", 
            title = title,
            text = text
        )
    }
}