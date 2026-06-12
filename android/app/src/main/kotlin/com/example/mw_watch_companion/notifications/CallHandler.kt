package com.example.mw_watch_companion.notifications

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.example.mw_watch_companion.common.MWNotification
import com.example.mw_watch_companion.common.PhoneApp

class CallHandler : NotificationHandler {
    override fun canHandle(sbn: StatusBarNotification): Boolean {
        val pkg = sbn.packageName.lowercase()
        return pkg.contains("dialer") || pkg.contains("telecom") || pkg.contains("phone")
    }

    override fun process(sbn: StatusBarNotification): MWNotification? {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: "Call"
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: "Incoming call"
        
        return MWNotification(
            appId = PhoneApp.CALL,
            appName = "Call",
            title = title,
            text = text
        )
    }
}