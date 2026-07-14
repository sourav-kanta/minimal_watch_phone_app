package com.example.mw_watch_companion.notifications

import android.service.notification.StatusBarNotification

interface NotificationHandler {
    fun canHandle(sbn: StatusBarNotification): Boolean
    fun process(sbn: StatusBarNotification): MWNotification?
}