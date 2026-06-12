package com.example.mw_watch_companion.common

interface NotificationForwarder {
    fun forwardNotification(notification: UByteArray)
}