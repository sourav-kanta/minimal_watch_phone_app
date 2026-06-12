package com.example.mw_watch_companion.notifications

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.example.mw_watch_companion.common.MWNotification
import com.example.mw_watch_companion.common.PhoneApp

class MessagingHandler : NotificationHandler {
    override fun canHandle(sbn: StatusBarNotification): Boolean {
        val pkg = sbn.packageName.toString().lowercase()
        return pkg.contains("messaging") || pkg.contains("mms") || pkg.contains("sms")
    }

    override fun process(sbn: StatusBarNotification): MWNotification? {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: "Message"
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        
        return MWNotification(
            appId = PhoneApp.MESSAGE,
            appName = "Message",
            title = title,
            text = text
        )
    }
}