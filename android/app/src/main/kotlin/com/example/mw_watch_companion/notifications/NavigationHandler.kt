package com.example.mw_watch_companion.notifications

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.example.mw_watch_companion.common.PhoneApp

class NavigationHandler : NotificationHandler {

    private fun String.removeMultibyte(): String {
        return this.replace(Regex("[^\\x00-\\x7F]"), "")
    }


    override fun canHandle(sbn: StatusBarNotification): Boolean {
        val pkg = sbn.packageName.toString().lowercase().removeMultibyte()
        return pkg.contains("maps") || pkg.contains("waze")
    }

    override fun process(sbn: StatusBarNotification): MWNotification? {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()?.removeMultibyte() ?: "Navigation"
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()?.removeMultibyte() ?: ""
        
        return MWNotification(
            appId = PhoneApp.NAVIGATION,
            appName = "Navigation",
            title = title,
            text = text
        )
    }
}