package com.example.mw_watch_companion.notifications

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.example.mw_watch_companion.common.PhoneApp

class WhatsAppHandler : NotificationHandler {

    private fun String.removeMultibyte(): String {
        return this.replace(Regex("[^\\x00-\\x7F]"), "")
    }

    var dropList = arrayOf(
        "new message"
    )

    override fun canHandle(sbn: StatusBarNotification): Boolean {
        return sbn.packageName.toString().contains("whatsapp", ignoreCase = true)
    }

    override fun process(sbn: StatusBarNotification): MWNotification? {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.removeMultibyte() ?: "WhatsApp"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.removeMultibyte() ?: ""
        
        return MWNotification(
            appId = PhoneApp.WHATSAPP,
            appName = "WhatsApp", 
            title = title,
            text = text
        )
    }
}