package com.example.mw_watch_companion.notifications

import android.service.notification.StatusBarNotification
import com.example.mw_watch_companion.common.MWNotification

interface NotificationHandler {
    fun canHandle(sbn: StatusBarNotification): Boolean
    fun process(sbn: StatusBarNotification): MWNotification?
}