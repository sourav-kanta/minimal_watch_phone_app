package com.example.mw_watch_companion.notifications

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.example.mw_watch_companion.common.PhoneApp

class DefaultHandler : NotificationHandler {
    
    private fun String.removeMultibyte(): String {
        return this.replace(Regex("[^\\x00-\\x7F]"), "")
    }

    override fun canHandle(sbn: StatusBarNotification): Boolean = true

    override fun process(sbn: StatusBarNotification): MWNotification? {
        val extras = sbn.notification.extras
        
        if(sbn.isOngoing) return null

        val rawPkg = sbn.packageName.toString().removeMultibyte() ?: "Unknown"
        val appName = rawPkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.removeMultibyte() ?: appName
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.removeMultibyte() ?: ""
        val longText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.removeMultibyte() ?: ""

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