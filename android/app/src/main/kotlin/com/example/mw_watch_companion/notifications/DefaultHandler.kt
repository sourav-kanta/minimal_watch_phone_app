package com.example.mw_watch_companion.notifications

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.example.mw_watch_companion.common.MWNotification
import com.example.mw_watch_companion.common.PhoneApp

class DefaultHandler : NotificationHandler {
    
    override fun canHandle(sbn: StatusBarNotification): Boolean = true

    override fun process(sbn: StatusBarNotification): MWNotification? {
        val extras = sbn.notification.extras
        
        if(sbn.isOngoing) return null

        val rawPkg = sbn.packageName.toString() ?: "Unknown"
        val appName = rawPkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: appName
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val longText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val finalBody = when {
            longText.isBlank() -> text
            longText.contains(text) -> longText 
            else -> "$text $longText"
        }

        return MWNotification(
            appId = PhoneApp.UNKNOWN,
            appName = appName.take(13), 
            title = title,
            text = finalBody
        )
    }
}